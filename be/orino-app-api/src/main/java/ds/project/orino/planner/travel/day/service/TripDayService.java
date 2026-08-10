package ds.project.orino.planner.travel.day.service;

import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.TripDay;
import ds.project.orino.domain.planner.travel.repository.TravelPlaceRepository;
import ds.project.orino.domain.planner.travel.repository.TripDayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 여행 날짜와 기준 도시를 다루는 한 곳. <b>여행에는 타임존이 없다</b>는 v2.1의 전제를 지키려면
 * 타임존·통화·좌표를 찾는 경로가 하나여야 한다 — 흩어 두면 어떤 화면은 새 도시를 보여주는데
 * 알림은 옛 시각에 가는, 사용자가 알아차릴 방법이 없는 오류가 난다.
 *
 * <p><b>날짜 집합은 항상 기간과 일치한다.</b> 비어 있는 날짜는 타임존이 없는 날이고, 그 순간
 * 알림·날씨·상태 판정이 전부 죽는다. 그래서 기간을 바꾸는 쪽은 반드시
 * {@link #syncPeriod(Trip, Long)}을 같은 트랜잭션에서 부른다.
 *
 * <p>지금은 조회 대부분이 {@link #primaryCity(Long)}(첫날의 기준 도시)을 쓴다 — 마이그레이션
 * 직후의 모든 여행이 단일 도시라 v2.0과 결과가 같다. 값마다 어느 날짜의 도시를 봐야 하는지
 * (상태는 오늘, D-day는 첫날, 보드 헤더는 선택한 날짜)는 #1123에서 갈라낸다.
 */
@Service
@Transactional(readOnly = true)
public class TripDayService {

    private final TripDayRepository dayRepository;
    private final TravelPlaceRepository placeRepository;

    public TripDayService(TripDayRepository dayRepository, TravelPlaceRepository placeRepository) {
        this.dayRepository = dayRepository;
        this.placeRepository = placeRepository;
    }

    /**
     * 여행 기간의 날짜 행을 만들거나 지워 기간과 일치시킨다.
     *
     * <ul>
     *   <li>늘어난 날짜는 <b>직전(마지막) 날짜의 기준 도시를 상속</b>한다</li>
     *   <li>잘린 날짜는 행을 지운다 — 그 날짜 일정의 보관함 이동은 호출부가 따로 처리한다</li>
     *   <li>날짜가 하나도 없으면(신규 생성) 전 날짜에 {@code fallbackBaseCityId}를 채운다</li>
     * </ul>
     *
     * <p>{@code cityMemo}는 날짜 기준으로 살아남는다. 기간 안에 남아 있는 날짜의 행을 지우지
     * 않기 때문에, 도시가 바뀌어도 그 날짜에 적어 둔 메모는 그대로다.
     */
    @Transactional
    public void syncPeriod(Trip trip, Long fallbackBaseCityId) {
        Map<LocalDate, TripDay> existing = dayRepository
                .findAllByTripIdOrderByDayDateAsc(trip.getId()).stream()
                .collect(Collectors.toMap(TripDay::getDayDate, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));

        List<TripDay> outside = existing.entrySet().stream()
                .filter(entry -> !trip.covers(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        dayRepository.deleteAll(outside);
        outside.forEach(day -> existing.remove(day.getDayDate()));

        // 새 날짜는 "바로 앞 날짜"의 도시를 물려받는다. 시작일이 앞으로 당겨져 앞에 붙는
        // 날짜에는 앞 날짜가 없으므로 첫날의 도시를 쓴다 — 도쿄에서 시작하는 여행에 하루를
        // 더 붙이면 그 하루도 도쿄다.
        Long inherited = existing.isEmpty() ? fallbackBaseCityId
                : existing.values().iterator().next().getBasePlaceId();

        List<TripDay> created = new ArrayList<>();
        for (LocalDate date = trip.getStartDate(); !date.isAfter(trip.getEndDate());
                date = date.plusDays(1)) {
            TripDay day = existing.get(date);
            if (day == null) {
                created.add(new TripDay(trip.getId(), date, inherited));
            } else {
                inherited = day.getBasePlaceId();
            }
        }
        dayRepository.saveAll(created);
    }

    /**
     * 전 날짜의 기준 도시를 하나로 맞춘다. 여행 전체의 목적지를 한 번에 바꾸는 화면(v2.0 폼)만
     * 쓴다 — 날짜별로 다르게 두는 길은 기준 도시 변경 API(#1122)다.
     */
    @Transactional
    public void rebaseAll(Long tripId, Long basePlaceId) {
        List<TripDay> days = dayRepository.findAllByTripIdOrderByDayDateAsc(tripId);
        days.forEach(day -> day.changeBaseCity(basePlaceId));
        dayRepository.saveAll(days);
    }

    /** 여행 전체의 날짜 → 기준 도시. 도시는 여러 날짜가 공유하므로 장소는 한 번씩만 읽는다. */
    public Map<LocalDate, TravelPlace> baseCitiesOf(Long tripId) {
        List<TripDay> days = dayRepository.findAllByTripIdOrderByDayDateAsc(tripId);
        if (days.isEmpty()) {
            return Map.of();
        }
        Map<Long, TravelPlace> places = placeRepository
                .findAllById(days.stream().map(TripDay::getBasePlaceId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(TravelPlace::getId, Function.identity()));

        Map<LocalDate, TravelPlace> byDate = new LinkedHashMap<>();
        for (TripDay day : days) {
            TravelPlace city = places.get(day.getBasePlaceId());
            if (city != null) {
                byDate.put(day.getDayDate(), city);
            }
        }
        return byDate;
    }

    /**
     * 여행 여럿의 첫날 기준 도시를 한 번에. 목록·요약 화면이 여행 수만큼 조회하지 않게 한다.
     *
     * <p>날짜 행이 없는 여행은 지도에서 빠진다 — 목록 전체를 죽이는 대신 호출부가 그 한 건만
     * 기기 타임존으로 판정하게 둔다. 만들어질 수 없는 상태라 정상 경로에서는 비지 않는다.
     */
    public Map<Long, TravelPlace> primaryCitiesOf(Collection<Long> tripIds) {
        if (tripIds.isEmpty()) {
            return Map.of();
        }
        List<TripDay> days = dayRepository.findAllByTripIdInOrderByTripIdAscDayDateAsc(tripIds);
        Map<Long, TravelPlace> places = placeRepository
                .findAllById(days.stream().map(TripDay::getBasePlaceId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(TravelPlace::getId, Function.identity()));

        Map<Long, TripDay> firstDays = new LinkedHashMap<>();
        for (TripDay day : days) {
            firstDays.merge(day.getTripId(), day,
                    (kept, next) -> next.getDayDate().isBefore(kept.getDayDate()) ? next : kept);
        }
        Map<Long, TravelPlace> byTrip = new LinkedHashMap<>();
        firstDays.forEach((tripId, day) -> {
            TravelPlace city = places.get(day.getBasePlaceId());
            if (city != null) {
                byTrip.put(tripId, city);
            }
        });
        return byTrip;
    }

    /**
     * 그 날짜의 기준 도시. 기간 밖 날짜를 물으면 가장 가까운 끝(첫날/마지막 날)의 도시를 준다 —
     * 상태 판정처럼 "오늘"이 기간 밖일 수 있는 호출부가 있고, 거기서 도시가 비면 판정 자체를
     * 못 한다.
     */
    public TravelPlace baseCityOn(Long tripId, LocalDate date) {
        Map<LocalDate, TravelPlace> cities = baseCitiesOf(tripId);
        requireDays(tripId, cities);

        TravelPlace exact = cities.get(date);
        if (exact != null) {
            return exact;
        }
        List<LocalDate> dates = List.copyOf(cities.keySet());
        LocalDate first = dates.getFirst();
        return date.isBefore(first) ? cities.get(first) : cities.get(dates.getLast());
    }

    /**
     * 첫날의 기준 도시. v2.0에서 {@code trip}이 들고 있던 목적지가 이 자리로 내려왔다 —
     * D-day처럼 "여행이 시작되는 곳"을 기준으로 하는 값이 쓴다.
     */
    public TravelPlace primaryCity(Long tripId) {
        Map<LocalDate, TravelPlace> cities = baseCitiesOf(tripId);
        requireDays(tripId, cities);
        return cities.values().iterator().next();
    }

    /** 첫날 기준 도시의 타임존. */
    public ZoneId primaryZone(Long tripId) {
        return zoneOf(primaryCity(tripId));
    }

    /** 그 날짜 기준 도시의 타임존. */
    public ZoneId zoneOn(Long tripId, LocalDate date) {
        return zoneOf(baseCityOn(tripId, date));
    }

    /**
     * 도시의 타임존. 도시 장소에는 타임존이 반드시 있어야 하지만, 값이 비어 있다고 화면 전체를
     * 죽이지는 않는다 — 그런 도시는 애초에 기준 도시로 지정될 수 없으므로 여기 오면 데이터
     * 문제이고, 시스템 기본값으로 버티면서 로그가 아니라 화면의 시각으로 드러나게 둔다.
     */
    public static ZoneId zoneOf(TravelPlace city) {
        return city.getTimezone() == null ? ZoneId.systemDefault()
                : ZoneId.of(city.getTimezone());
    }

    /**
     * 날짜 행이 하나도 없는 여행은 v2.1에서 존재할 수 없다. 조용히 기본값으로 때우면 그
     * 화면만 틀린 시각을 보여주므로, 만들어질 수 없는 상태라는 걸 드러낸다.
     */
    private void requireDays(Long tripId, Map<LocalDate, TravelPlace> cities) {
        if (cities.isEmpty()) {
            throw new IllegalStateException(
                    "여행 %d에 기준 도시가 붙은 날짜가 없습니다.".formatted(tripId));
        }
    }
}
