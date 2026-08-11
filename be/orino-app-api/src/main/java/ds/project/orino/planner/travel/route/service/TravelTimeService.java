package ds.project.orino.planner.travel.route.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.TripActivity;
import ds.project.orino.domain.planner.travel.repository.TravelPlaceRepository;
import ds.project.orino.planner.travel.route.client.RoutesClient;
import ds.project.orino.planner.travel.route.client.TravelMode;
import ds.project.orino.planner.travel.route.config.RoutesProperties;
import ds.project.orino.planner.travel.route.dto.TravelTimeResponse;
import ds.project.orino.redis.planner.travel.RouteCacheRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 연속한 두 일정 사이 이동시간(§4.4).
 *
 * <p>일정 리스트에 항상 표시된다 — 현지에서 계획을 따라갈 수 있는지는 "다음 장소까지 얼마나
 * 걸리는지"가 가른다.
 */
@Service
public class TravelTimeService {

    private final TravelPlaceRepository placeRepository;
    private final RoutesClient routesClient;
    private final RouteCacheRepository cacheRepository;
    private final RoutesProperties props;
    private final ObjectMapper objectMapper;

    public TravelTimeService(TravelPlaceRepository placeRepository,
                             RoutesClient routesClient,
                             RouteCacheRepository cacheRepository,
                             RoutesProperties props,
                             ObjectMapper objectMapper) {
        this.placeRepository = placeRepository;
        this.routesClient = routesClient;
        this.cacheRepository = cacheRepository;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * 정렬된 일정 목록에서 이동시간을 만든다.
     *
     * <p><b>장소 없는 일정은 건너뛴다</b>(§4.4) — "점심"처럼 장소를 안 정한 일정이 사이에 끼어도
     * 앞뒤 장소 사이 이동은 여전히 알고 싶다. 그걸 끊으면 정작 필요한 이동시간이 사라진다.
     *
     * <p><b>도시 경계를 넘는 구간은 계산하지 않는다</b>(§3.4) — 표시용 값만 만들고 외부 호출도
     * 하지 않는다.
     */
    public List<TravelTimeResponse> travelTimes(List<TripActivity> ordered) {
        List<Located> located = locate(ordered);
        List<TravelTimeResponse> travelTimes = new ArrayList<>();
        for (int i = 0; i + 1 < located.size(); i++) {
            travelTimes.add(travelTime(located.get(i), located.get(i + 1)));
        }
        return travelTimes;
    }

    /**
     * 이동수단 시트(§S-04)가 여는 단건 조회. 자동 판정되지 않은 수단은 여기서만 계산한다.
     *
     * <p>보드에서 두 수단을 다 계산해 두지 않는다 — 호출당 과금이라 아무도 안 열어 볼 값까지
     * 미리 사게 된다. 시트를 열었을 때만 부르고, 보드와 <b>같은 캐시</b>를 태워 두 번째부터는
     * 외부 호출이 없다.
     */
    public TravelTimeResponse travelTimeBetween(List<TripActivity> ordered, Long fromActivityId,
                                                Long toActivityId, TravelMode mode) {
        Map<Long, Located> located = locate(ordered).stream()
                .collect(Collectors.toMap(Located::activityId, Function.identity()));
        Located from = located.get(fromActivityId);
        Located to = located.get(toActivityId);
        if (from == null || to == null) {
            // 좌표가 없으면 이동 자체가 성립하지 않는다 — 화면에도 이동시간 행이 없다.
            throw new CustomException(ErrorCode.TRAVEL_TIME_NOT_AVAILABLE);
        }
        return travelTime(from, to, mode);
    }

    /**
     * 그날 <b>마지막 일정에서 어떤 장소까지</b>의 이동. 숙소 이동 행(§3.5)이 쓴다.
     *
     * <p>일정 사이 이동과 같은 규칙·같은 캐시를 탄다 — 좌표 쌍이 키라 숙소든 일정이든 두 지점
     * 사이 거리는 같은 값이다. <b>도시 경계 규칙도 같다</b> — 마지막 일정과 목적지가 다른
     * 도시면 계산하지 않는다(§3.4).
     *
     * <p>마지막 일정에 좌표가 없거나 목적지에 좌표가 없으면 <b>비어 있는 값</b>을 준다. 이동이
     * 성립하지 않는 것을 0분으로 답하면 화면이 "바로 옆"이라고 읽는다.
     */
    public Optional<Move> moveToPlace(List<TripActivity> ordered, TravelPlace destination) {
        if (destination.getLat() == null || destination.getLng() == null) {
            return Optional.empty();
        }
        List<Located> located = locate(ordered);
        if (located.isEmpty()) {
            return Optional.empty();
        }
        Located from = located.get(located.size() - 1);
        Located to = new Located(null, destination.getLat(), destination.getLng(),
                destination.getCityPlaceRef());
        if (crossesCity(from, to)) {
            return Optional.of(Move.crossingCity());
        }
        TravelMode mode = autoMode(from, to);
        return Optional.of(route(from, to, mode)
                .map(r -> new Move(mode, Math.round(r.durationSeconds() / 60f), false))
                // 실패해도 수단은 남긴다 — 거리만 아는 상태와 아무것도 모르는 상태는 다르다.
                .orElseGet(() -> new Move(mode, null, false)));
    }

    /**
     * 출발 알림을 켤 수 있는 일정 id.
     *
     * <p>두 조건을 다 넘어야 한다 — <b>직전에 장소 있는 일정이 있고</b>(어디서 출발하는지 모르면
     * 언제 나서야 하는지도 모른다), <b>그 사이가 도시를 넘지 않는다</b>(도시를 넘는 이동은
     * 계산하지 않으므로 알림 시각을 정할 수 없다, §3.4).
     *
     * <p><b>외부 호출이 없다</b> — 도시 판정은 저장된 식별자만 보고, 소요 시간은 여기서 묻지
     * 않는다. 일정을 열 때마다 유료 호출이 나가면 안 된다.
     *
     * <p>계산이 일시적으로 실패한 구간({@code fallback})은 <b>켤 수 있다고 본다</b>. 스위치는
     * 저장되는 설정이라, 잠깐의 외부 장애로 끄면 복구된 뒤에도 사용자가 꺼 둔 것과 구분되지
     * 않는다.
     */
    public Set<Long> departureNotifiable(List<TripActivity> ordered) {
        List<Located> located = locate(ordered);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i + 1 < located.size(); i++) {
            if (!crossesCity(located.get(i), located.get(i + 1))) {
                ids.add(located.get(i + 1).activityId());
            }
        }
        return ids;
    }

    /** 이동 한 건의 표시값. 도착지가 일정이 아니라 장소라 일정 id가 없다. */
    public record Move(TravelMode mode, Integer durationMinutes, boolean crossCity) {

        /** 도시를 넘는 이동 — 수단도 소요 시간도 없다. */
        static Move crossingCity() {
            return new Move(null, null, true);
        }
    }

    /** 좌표를 가진 일정만 순서대로 남긴다. 장소가 있어도 좌표가 없으면(직접 입력) 뺀다. */
    private List<Located> locate(List<TripActivity> ordered) {
        List<Long> placeIds = ordered.stream()
                .map(TripActivity::getPlaceId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (placeIds.isEmpty()) {
            return List.of();
        }
        Map<Long, TravelPlace> places = placeRepository.findAllByIdIn(placeIds).stream()
                .collect(Collectors.toMap(TravelPlace::getId, Function.identity()));

        List<Located> located = new ArrayList<>();
        for (TripActivity activity : ordered) {
            if (activity.getPlaceId() == null) {
                continue;
            }
            TravelPlace place = places.get(activity.getPlaceId());
            if (place == null || place.getLat() == null || place.getLng() == null) {
                continue;
            }
            located.add(new Located(activity.getId(), place.getLat(), place.getLng(),
                    place.getCityPlaceRef()));
        }
        return located;
    }

    /** 도시 판정은 장소에 저장된 식별자로만 한다(D-23) — 좌표 거리로 추측하지 않는다. */
    private static boolean crossesCity(Located from, Located to) {
        return TravelPlace.crossesCity(from.cityPlaceRef(), to.cityPlaceRef());
    }

    private TravelTimeResponse travelTime(Located from, Located to) {
        // 수단은 직선거리로 정한다(§1.3). 경로 거리로 정하면 수단을 알기 위해 경로가 필요하고,
        // 경로를 얻으려면 수단이 필요해 순환한다.
        return travelTime(from, to, autoMode(from, to));
    }

    private TravelMode autoMode(Located from, Located to) {
        int straightM = Haversine.distanceM(from.lat(), from.lng(), to.lat(), to.lng());
        return straightM <= props.walkThresholdM() ? TravelMode.WALK : TravelMode.DRIVE;
    }

    private TravelTimeResponse travelTime(Located from, Located to, TravelMode mode) {
        int straightM = Haversine.distanceM(from.lat(), from.lng(), to.lat(), to.lng());

        // 도시를 넘으면 여기서 끝낸다(§3.4). 이동수단 시트가 수단을 지정해 물어와도 마찬가지다 —
        // 판정이 갈리면 시트가 보드에 없는 값을 보여주게 된다.
        if (crossesCity(from, to)) {
            return TravelTimeResponse.crossCity(from.activityId(), to.activityId(), straightM);
        }

        return route(from, to, mode)
                .map(r -> new TravelTimeResponse(from.activityId(), to.activityId(), mode,
                        Math.round(r.durationSeconds() / 60f), r.distanceM(), false, false))
                // 실패해도 이동시간 행 자체는 남긴다 — 거리만이라도 알면 계획을 세울 수 있다.
                .orElseGet(() -> new TravelTimeResponse(from.activityId(), to.activityId(), mode,
                        null, straightM, true, false));
    }

    /**
     * 캐시를 먼저 본다. 보드는 열 때마다 조회되고 날짜 탭을 넘길 때마다 다시 온다 —
     * 캐시가 없으면 탭 하나 넘길 때마다 일정 수만큼 유료 호출이 난다.
     */
    private Optional<RoutesClient.Route> route(Located from, Located to, TravelMode mode) {
        String key = travelTimeKey(from, to, mode);
        Optional<String> hit = cacheRepository.find(key);
        if (hit.isPresent()) {
            return Optional.of(objectMapper.readValue(hit.get(), RoutesClient.Route.class));
        }
        Optional<RoutesClient.Route> fresh = routesClient.route(
                new RoutesClient.Coordinates(from.lat(), from.lng()),
                new RoutesClient.Coordinates(to.lat(), to.lng()),
                mode);
        // 실패는 캐시하지 않는다 — 일시적 실패를 붙들고 있으면 복구된 뒤에도 계속 fallback이다.
        fresh.ifPresent(r ->
                cacheRepository.save(key, objectMapper.writeValueAsString(r), props.cacheTtl()));
        return fresh;
    }

    /**
     * 좌표 쌍 + 이동수단. 일정 id를 넣지 않는다 — 순서를 바꿔도 같은 두 장소 사이 이동은
     * 그대로 재사용되어야 한다(§4.4).
     */
    private static String travelTimeKey(Located from, Located to, TravelMode mode) {
        return "%s,%s:%s,%s:%s".formatted(
                round(from.lat()), round(from.lng()), round(to.lat()), round(to.lng()), mode);
    }

    /** 소수점 5자리(약 1m). 좌표가 미세하게 달라도 같은 이동으로 본다. */
    private static BigDecimal round(BigDecimal value) {
        return value.setScale(5, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private record Located(Long activityId, BigDecimal lat, BigDecimal lng, String cityPlaceRef) {
    }
}
