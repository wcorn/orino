package ds.project.orino.planner.travel.place.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.repository.TravelPlaceRepository;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.place.client.PlaceResult;
import ds.project.orino.planner.travel.place.client.PlacesClient;
import ds.project.orino.planner.travel.place.config.PlacesProperties;
import ds.project.orino.planner.travel.place.dto.CityResponse;
import ds.project.orino.planner.travel.place.dto.PlaceCreateRequest;
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
    private final PlacesProperties props;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PlaceService(PlacesClient placesClient,
                        PlaceSearchCacheRepository cache,
                        TravelPlaceRepository placeRepository,
                        TripRepository tripRepository,
                        PlacesProperties props,
                        ObjectMapper objectMapper,
                        Clock clock) {
        this.placesClient = placesClient;
        this.cache = cache;
        this.placeRepository = placeRepository;
        this.tripRepository = tripRepository;
        this.props = props;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * S-03 목적지 검색. 도시와 함께 <b>타임존·통화를 확정해서</b> 준다.
     */
    public List<CityResponse> searchCities(String query) {
        List<PlaceResult> results = cached(
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
     * S-06 장소 검색. `tripId`를 주면 그 여행의 목적지 좌표 주변을 우선한다(§1.5).
     */
    public List<PlaceSearchResult> searchPlaces(Long memberId, String query, Long tripId) {
        PlacesClient.Coordinates bias = biasOf(memberId, tripId);
        String biasKey = bias == null ? "none" : bias.lat() + "," + bias.lng();

        List<PlaceResult> results = cached(
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
            placesClient.fetchDetails(place.getGooglePlaceId()).ifPresent(fresh -> {
                place.updateBasics(fresh.address(), fresh.lat(), fresh.lng(),
                        fresh.category(), fresh.rating());
                place.updateDetails(fresh.phone(), fresh.openingHours(), clock.instant());
            });
        }
        return PlaceDetail.from(place);
    }

    /**
     * 구글 장소를 담는다. 같은 장소를 두 번 저장하지 않는다 — 이미 있으면 그걸 돌려준다
     * ({@code uk_place_member_google}이 멤버당 한 행을 강제한다).
     */
    @Transactional
    public TravelPlace upsertFromGoogle(Long memberId, String googlePlaceId) {
        Optional<TravelPlace> existing =
                placeRepository.findByMemberIdAndGooglePlaceId(memberId, googlePlaceId);
        if (existing.isPresent()) {
            return existing.get();
        }
        PlaceResult fresh = placesClient.fetchDetails(googlePlaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_PLACE_NOT_FOUND));

        TravelPlace place = TravelPlace.fromGoogle(memberId, googlePlaceId, fresh.name());
        place.updateBasics(fresh.address(), fresh.lat(), fresh.lng(),
                fresh.category(), fresh.rating());
        place.updateDetails(fresh.phone(), fresh.openingHours(), clock.instant());
        return placeRepository.save(place);
    }

    /** 직접 입력. 검색에 안 나오는 곳도 일정에 넣을 수 있어야 한다. */
    @Transactional
    public PlaceDetail createManual(Long memberId, PlaceCreateRequest request) {
        TravelPlace place = TravelPlace.manual(memberId, request.name().trim());
        place.updateBasics(request.address(), request.lat(), request.lng(), null, null);
        return PlaceDetail.from(placeRepository.save(place));
    }

    // ---------------- helpers ----------------

    /**
     * 캐시에 있으면 그대로, 없으면 불러서 채운다.
     *
     * <p>빈 결과는 캐시하지 않는다 — 일시적 실패(타임아웃·쿼터)도 빈 목록으로 떨어지는데,
     * 그걸 한 시간 동안 물고 있으면 복구된 뒤에도 계속 빈 화면이 된다.
     */
    private List<PlaceResult> cached(Supplier<Optional<String>> read,
                                     java.util.function.Consumer<String> write,
                                     Supplier<List<PlaceResult>> fetch) {
        Optional<String> hit = read.get();
        if (hit.isPresent()) {
            try {
                return objectMapper.readValue(hit.get(), new TypeReference<List<PlaceResult>>() {
                });
            } catch (Exception e) {
                log.warn("장소 캐시 역직렬화 실패, 새로 조회한다: {}", e.getMessage());
            }
        }
        List<PlaceResult> fresh = fetch.get();
        if (!fresh.isEmpty()) {
            write.accept(objectMapper.writeValueAsString(fresh));
        }
        return fresh;
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

    private PlacesClient.Coordinates biasOf(Long memberId, Long tripId) {
        if (tripId == null) {
            return null;
        }
        Trip trip = tripRepository.findByIdAndMemberId(tripId, memberId).orElse(null);
        if (trip == null || trip.getLat() == null || trip.getLng() == null) {
            return null;
        }
        return new PlacesClient.Coordinates(trip.getLat(), trip.getLng());
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
