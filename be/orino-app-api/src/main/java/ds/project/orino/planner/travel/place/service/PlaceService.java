package ds.project.orino.planner.travel.place.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.repository.TravelPlaceRepository;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.day.service.TripDayService;
import ds.project.orino.planner.travel.external.ExternalApiRejectedException;
import ds.project.orino.planner.travel.place.client.PlaceResult;
import ds.project.orino.planner.travel.place.client.PlacesClient;
import ds.project.orino.planner.travel.place.config.PlacesProperties;
import ds.project.orino.planner.travel.place.dto.CityResponse;
import ds.project.orino.planner.travel.place.dto.PlaceCreateRequest;
import ds.project.orino.planner.travel.metrics.ExternalApiMetrics;
import ds.project.orino.planner.travel.place.dto.PlaceDetail;
import ds.project.orino.planner.travel.place.dto.PlaceSearchResult;
import ds.project.orino.redis.planner.travel.PlaceSearchCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 장소 검색·상세 프록시. 브라우저가 Google API를 직접 부르지 않는다 —
 * 키를 노출하지 않고, 캐시로 호출당 과금을 줄이며, 응답 형태를 우리가 정하기 위해서다.
 */
@Service
@Transactional(readOnly = true)
public class PlaceService {

    private static final Logger log = LoggerFactory.getLogger(PlaceService.class);

    private final PlacesClient placesClient;
    private final PlaceSearchCacheRepository cache;
    private final TravelPlaceRepository placeRepository;
    private final TripRepository tripRepository;
    private final TripDayService tripDayService;
    private final PlacesProperties props;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ExternalApiMetrics metrics;

    public PlaceService(PlacesClient placesClient,
                        PlaceSearchCacheRepository cache,
                        TravelPlaceRepository placeRepository,
                        TripRepository tripRepository,
                        TripDayService tripDayService,
                        PlacesProperties props,
                        ObjectMapper objectMapper,
                        Clock clock,
                        ExternalApiMetrics metrics) {
        this.placesClient = placesClient;
        this.cache = cache;
        this.placeRepository = placeRepository;
        this.tripRepository = tripRepository;
        this.tripDayService = tripDayService;
        this.props = props;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.metrics = metrics;
    }

    /**
     * S-03 목적지 검색. 도시와 함께 <b>타임존·통화를 확정해서</b> 준다.
     */
    public List<CityResponse> searchCities(String query) {
        List<PlaceResult> results = cached(ExternalApiMetrics.Api.PLACES_CITY,
                () -> cache.findCity(query),
                json -> cache.saveCity(query, json, props.searchTtl()),
                () -> placesClient.searchCities(query));

        return results.stream()
                // 타임존을 못 얻으면 목적지로 쓸 수 없다 — 여행의 모든 시각 계산이 여기서 나온다.
                .filter(r -> r.timezone() != null)
                .map(r -> new CityResponse(r.googlePlaceId(), r.name(), r.address(),
                        r.lat(), r.lng(), r.timezone(), currencyOf(r.countryCode())))
                .toList();
    }

    /**
     * S-06 장소 검색. `tripId`를 주면 그 여행의 도시 좌표 주변을 우선한다(§1.5).
     *
     * <p>{@code cityPlaceId}(기준 도시 칩)를 주면 <b>그 도시</b>로 편향한다. 안 주면 첫날 도시로
     * 떨어지는데, 다구간 여행에서 그건 곧 "오사카 좌표로 교토 가게를 찾는" 상태다.
     */
    public List<PlaceSearchResult> searchPlaces(Long memberId, String query, Long tripId,
                                                Long cityPlaceId) {
        PlacesClient.Coordinates bias = biasOf(memberId, tripId, cityPlaceId);
        String biasKey = bias == null ? "none" : bias.lat() + "," + bias.lng();

        List<PlaceResult> results = cached(ExternalApiMetrics.Api.PLACES_SEARCH,
                () -> cache.findSearch(query, biasKey),
                json -> cache.saveSearch(query, biasKey, json, props.searchTtl()),
                () -> placesClient.searchPlaces(query, bias));

        // 이미 담아 둔 장소는 내부 id를 실어 준다 — FE가 "담기" 대신 상태를 보여줄 수 있다.
        Map<String, Long> savedIds = savedIdsOf(memberId, results);

        return results.stream()
                .map(r -> new PlaceSearchResult(
                        savedIds.get(r.googlePlaceId()), r.googlePlaceId(), r.name(),
                        r.category(), r.address(), r.rating(),
                        r.lat(), r.lng()))
                .toList();
    }

    /**
     * 저장된 장소 상세. 캐시가 만료됐으면(§4.7 — 30일) 구글에서 다시 받아 채운다.
     */
    @Transactional
    public PlaceDetail detail(Long memberId, Long placeId) {
        TravelPlace place = placeRepository.findByIdAndMemberId(placeId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_PLACE_NOT_FOUND));

        if (place.getGooglePlaceId() != null && place.needsDetailsRefresh(clock.instant())) {
            // 갱신에 실패해도 저장된 값으로 답한다 — 30일 지난 영업시간이 빈 화면보다 낫다.
            Optional<PlaceResult> refreshed = fetchDetailsOrEmpty(place.getGooglePlaceId());
            refreshed.ifPresent(fresh -> {
                place.updateBasics(fresh.address(), fresh.lat(), fresh.lng(),
                        fresh.category(), fresh.rating());
                place.updateDetails(fresh.phone(), fresh.openingHours(), clock.instant());
            });
        } else if (place.getGooglePlaceId() != null) {
            // 상세 캐시는 Redis가 아니라 행의 갱신 시각이다(30일, §4.7) — 갱신이 필요 없었다는
            // 것이 곧 히트다. 세지 않으면 이 계열의 히트율이 영영 0으로 보인다.
            metrics.record(ExternalApiMetrics.Api.PLACES_DETAILS, ExternalApiMetrics.Result.HIT);
        }
        return PlaceDetail.from(place);
    }

    /** 기준 도시를 모르는 자리(구간 입력 등)에서 담을 때. 장소는 도시 식별자 없이 저장된다. */
    @Transactional
    public TravelPlace upsertFromGoogle(Long memberId, String googlePlaceId) {
        return upsertFromGoogle(memberId, googlePlaceId, null);
    }

    /**
     * 구글 장소를 담는다. 같은 장소를 두 번 저장하지 않는다 — 이미 있으면 그걸 돌려준다
     * ({@code uk_place_member_google}이 멤버당 한 행을 강제한다).
     *
     * <p>{@code cityPlaceId}(기준 도시 칩, S-06)가 오면 <b>그 도시의 식별자를 함께 저장한다.</b>
     * 이 값이 없으면 보관함 도시 그룹도 도시 이탈 표시도 성립하지 않는다 — 구글 상세 응답은
     * 도시 <i>이름</i>만 주고 도시의 장소 id는 주지 않기 때문에, 어느 도시에서 찾았는지는
     * 화면만 안다. 좌표 거리로 도시를 되짚지 않는 이유는 D-23에 있다.
     */
    @Transactional
    public TravelPlace upsertFromGoogle(Long memberId, String googlePlaceId, Long cityPlaceId) {
        Optional<TravelPlace> existing =
                placeRepository.findByMemberIdAndGooglePlaceId(memberId, googlePlaceId);
        if (existing.isPresent()) {
            // 이미 담긴 장소의 도시는 덮지 않는다 — 나중에 다른 도시 칩으로 다시 담았다고
            // 처음 알던 도시가 바뀌면, 그 장소가 붙은 다른 날짜의 판정까지 조용히 흔들린다.
            // 행 하나로 호출 한 번을 아낀 것이라 히트로 센다.
            metrics.record(ExternalApiMetrics.Api.PLACES_DETAILS, ExternalApiMetrics.Result.HIT);
            return existing.get();
        }
        PlaceResult fresh = requireDetails(googlePlaceId);

        TravelPlace place = TravelPlace.fromGoogle(memberId, googlePlaceId, fresh.name());
        place.updateBasics(fresh.address(), fresh.lat(), fresh.lng(),
                fresh.category(), fresh.rating());
        place.updateDetails(fresh.phone(), fresh.openingHours(), clock.instant());
        // 도시 이름은 상세 응답 것을 쓴다(화면의 `· 오사카` 꼬리표). 식별자는 칩에서만 온다.
        place.updateCityInfo(fresh.cityName(), cityRefOf(memberId, cityPlaceId),
                fresh.countryCode());
        return placeRepository.save(place);
    }

    private String cityRefOf(Long memberId, Long cityPlaceId) {
        return cityPlaceId == null ? null : requireOwnedCity(memberId, cityPlaceId)
                .getCityPlaceRef();
    }

    /**
     * 구글 도시를 담고 <b>기준 도시로 쓸 수 있게 승격</b>한다. 구간 입력이 검색 결과를 그대로
     * 보내면 여기서 id로 바뀐다.
     *
     * <p>타임존은 도시로 쓰이는 장소의 필수 조건이라, 이미 담아 둔 장소인데 타임존이 비어
     * 있으면(일정 장소로 먼저 담긴 경우) 상세를 한 번 더 불러 채운다.
     */
    @Transactional
    public TravelPlace upsertCityFromGoogle(Long memberId, String googlePlaceId) {
        TravelPlace place = upsertFromGoogle(memberId, googlePlaceId);
        if (place.getTimezone() != null) {
            place.promoteToCity(place.getCityName() != null ? place.getCityName() : place.getName(),
                    place.getTimezone(), place.getCurrency());
            return place;
        }
        // 같은 경로에서 상세를 두 번째로 부르는 자리다 — 계측이 없으면 이 한 번이 안 보인다.
        // 타임존 없이는 도시로 쓸 수 없으므로 여기서는 저장된 값으로 버틸 수가 없다.
        PlaceResult fresh = requireDetails(googlePlaceId);
        place.promoteToCity(place.getName(), fresh.timezone(), currencyOf(fresh.countryCode()));
        place.updateCityInfo(place.getName(), googlePlaceId, fresh.countryCode());
        return place;
    }

    /**
     * 직접 입력. 검색에 안 나오는 곳도 일정에 넣을 수 있어야 한다.
     *
     * <p>{@code kind = CITY}면 도시로 저장한다 — 도시 검색이 못 찾는 곳으로 여행을 갈 때도
     * 기준 도시는 있어야 한다(그때는 타임존·통화를 사용자가 고른다).
     */
    @Transactional
    public PlaceDetail createManual(Long memberId, PlaceCreateRequest request) {
        TravelPlace place = TravelPlace.manual(memberId, request.name().trim());
        place.updateBasics(request.address(), request.lat(), request.lng(), null, null);
        if (request.isCity()) {
            requireValidTimezone(request.timezone());
            String currency = normalizedCurrency(request.currency());
            requireValidCurrency(currency);
            place.promoteToCity(request.name().trim(), request.timezone(), currency);
        }
        return PlaceDetail.from(placeRepository.save(place));
    }

    /**
     * IANA 지역 ID만 받는다. {@code ZoneId.of}는 {@code "UTC+09:00"} 같은 오프셋도 통과시키는데,
     * 오프셋은 서머타임을 모르므로 알림 시각 환산이 계절에 따라 어긋난다.
     */
    private void requireValidTimezone(String timezone) {
        if (timezone == null || !ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new CustomException(ErrorCode.TRAVEL_INVALID_TIMEZONE);
        }
    }

    private void requireValidCurrency(String currency) {
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CustomException(ErrorCode.TRAVEL_INVALID_CURRENCY);
        }
    }

    private String normalizedCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
    }

    // ---------------- helpers ----------------

    /**
     * 캐시에 있으면 그대로, 없으면 불러서 채운다.
     *
     * <p>빈 결과는 캐시하지 않는다 — 일시적 실패(타임아웃·쿼터)도 빈 목록으로 떨어지는데,
     * 그걸 한 시간 동안 물고 있으면 복구된 뒤에도 계속 빈 화면이 된다.
     */
    private List<PlaceResult> cached(ExternalApiMetrics.Api api,
                                     Supplier<Optional<String>> read,
                                     java.util.function.Consumer<String> write,
                                     Supplier<List<PlaceResult>> fetch) {
        Optional<String> hit = read.get();
        if (hit.isPresent()) {
            try {
                List<PlaceResult> cachedResults =
                        objectMapper.readValue(hit.get(), new TypeReference<List<PlaceResult>>() {
                        });
                metrics.record(api, ExternalApiMetrics.Result.HIT);
                return cachedResults;
            } catch (Exception e) {
                // 히트로 세지 않는다 — 아래에서 실제로 나가므로 miss가 뒤따른다.
                log.warn("장소 캐시 역직렬화 실패, 새로 조회한다: {}", e.getMessage());
            }
        }
        List<PlaceResult> fresh;
        try {
            fresh = fetch.get();
        } catch (ExternalApiRejectedException e) {
            // 캐시에 있는 검색어는 위에서 이미 답했다 — 여기 온 건 캐시가 없는 검색어다.
            // 빈 목록으로 돌려주면 화면이 "결과 없음"이라고 거짓말을 한다.
            metrics.recordRejected(api);
            throw new CustomException(ErrorCode.TRAVEL_EXTERNAL_REJECTED);
        }
        metrics.recordFetch(api, !fresh.isEmpty());
        if (!fresh.isEmpty()) {
            write.accept(objectMapper.writeValueAsString(fresh));
        }
        return fresh;
    }

    /**
     * 상세를 받아 온다. 거절당하면 <b>비어 있는 것으로 친다</b> — 담아 둔 장소 정보로 화면이
     * 성립하는 자리에서만 쓴다({@link #detail}).
     */
    private Optional<PlaceResult> fetchDetailsOrEmpty(String googlePlaceId) {
        try {
            Optional<PlaceResult> detail = placesClient.fetchDetails(googlePlaceId);
            metrics.recordFetch(ExternalApiMetrics.Api.PLACES_DETAILS, detail.isPresent());
            return detail;
        } catch (ExternalApiRejectedException e) {
            metrics.recordRejected(ExternalApiMetrics.Api.PLACES_DETAILS);
            log.warn("장소 상세 갱신을 거절당해 저장된 값으로 답한다: placeId={}", googlePlaceId);
            return Optional.empty();
        }
    }

    /**
     * 상세를 받아 온다. 거절당하면 503으로 올린다 — <b>보여 줄 값이 아예 없는</b> 자리다
     * (처음 담는 장소). 여기서 404를 주면 "존재하지 않는 장소"라고 잘못 말하게 된다.
     */
    private PlaceResult requireDetails(String googlePlaceId) {
        Optional<PlaceResult> detail;
        try {
            detail = placesClient.fetchDetails(googlePlaceId);
        } catch (ExternalApiRejectedException e) {
            metrics.recordRejected(ExternalApiMetrics.Api.PLACES_DETAILS);
            throw new CustomException(ErrorCode.TRAVEL_EXTERNAL_REJECTED);
        }
        metrics.recordFetch(ExternalApiMetrics.Api.PLACES_DETAILS, detail.isPresent());
        return detail.orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_PLACE_NOT_FOUND));
    }

    /** 국가 코드 → ISO 4217. 매핑 테이블을 만들지 않고 JDK가 아는 값을 쓴다. */
    private String currencyOf(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return null;
        }
        try {
            return Currency.getInstance(Locale.of("", countryCode)).getCurrencyCode();
        } catch (IllegalArgumentException e) {
            // 통화가 없는 지역(남극 등)이나 JDK가 모르는 코드. 사용자가 직접 고르면 된다.
            return null;
        }
    }

    /**
     * 검색 편향 좌표.
     *
     * <p>기준 도시 칩({@code ?city=})이 오면 그 도시를 쓰고, 없으면 <b>첫날 기준 도시</b>로
     * 떨어진다. 칩이 있는데 조용히 첫날로 떨어뜨리지는 않는다 — 화면이 "교토"라고 말하면서
     * 오사카 가게를 물어오는 것이 이 이슈가 없애려는 바로 그 상태다.
     */
    private PlacesClient.Coordinates biasOf(Long memberId, Long tripId, Long cityPlaceId) {
        if (cityPlaceId != null) {
            return coordinatesOf(requireOwnedCity(memberId, cityPlaceId));
        }
        if (tripId == null) {
            return null;
        }
        Trip trip = tripRepository.findByIdAndMemberId(tripId, memberId).orElse(null);
        if (trip == null) {
            return null;
        }
        return coordinatesOf(tripDayService.primaryCity(trip.getId()));
    }

    /** 좌표 없는 도시(직접 입력)는 편향할 수 없다 — 편향 없이 검색하는 편이 낫다. */
    private static PlacesClient.Coordinates coordinatesOf(TravelPlace city) {
        if (city.getLat() == null || city.getLng() == null) {
            return null;
        }
        return new PlacesClient.Coordinates(city.getLat(), city.getLng());
    }

    /**
     * 기준 도시로 쓸 수 있는 내 장소인지 확인한다. 남의 장소·없는 장소는 404, 도시가 아니면
     * 400이다 — 가게를 기준 도시로 쓸 수는 없다(D-23).
     */
    private TravelPlace requireOwnedCity(Long memberId, Long cityPlaceId) {
        TravelPlace city = placeRepository.findByIdAndMemberId(cityPlaceId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_PLACE_NOT_FOUND));
        if (!city.isCity()) {
            throw new CustomException(ErrorCode.TRAVEL_NOT_A_CITY);
        }
        return city;
    }

    private Map<String, Long> savedIdsOf(Long memberId, List<PlaceResult> results) {
        List<String> ids = results.stream()
                .map(PlaceResult::googlePlaceId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return placeRepository.findAllByMemberIdAndGooglePlaceIdIn(memberId, ids).stream()
                .collect(Collectors.toMap(TravelPlace::getGooglePlaceId, TravelPlace::getId));
    }
}
