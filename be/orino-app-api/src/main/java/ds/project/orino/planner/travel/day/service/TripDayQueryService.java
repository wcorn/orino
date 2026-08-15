package ds.project.orino.planner.travel.day.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripDay;
import ds.project.orino.domain.planner.travel.repository.TravelPlaceRepository;
import ds.project.orino.domain.planner.travel.repository.TripDayRepository;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.day.dto.BaseCityResponse;
import ds.project.orino.planner.travel.day.dto.CityLegResponse;
import ds.project.orino.planner.travel.day.dto.TripDayResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 날짜와 구간 조회. 구간은 {@link LegDeriver}로 <b>매번 파생</b>하므로 날짜를 바꾸면 다음
 * 조회에서 곧바로 반영되고, 구간과 날짜가 어긋나는 상태가 아예 존재하지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class TripDayQueryService {

    private final TripRepository tripRepository;
    private final TripDayRepository dayRepository;
    private final TravelPlaceRepository placeRepository;

    public TripDayQueryService(TripRepository tripRepository,
                               TripDayRepository dayRepository,
                               TravelPlaceRepository placeRepository) {
        this.tripRepository = tripRepository;
        this.dayRepository = dayRepository;
        this.placeRepository = placeRepository;
    }

    public List<TripDayResponse> days(Long memberId, Long tripId) {
        Trip trip = getOwned(memberId, tripId);
        List<TripDay> days = dayRepository.findAllByTripIdOrderByDayDateAsc(tripId);
        Map<Long, TravelPlace> cities = citiesOf(days);
        Map<LocalDate, Integer> legIndexes = legIndexByDate(days);

        // 도시가 바뀌는 날 → 떠나온 도시. 키가 있다는 것 자체가 "바뀌었다"이므로
        // cityChanged와 arrivingFrom이 한 곳에서 나온다 — 둘이 어긋날 수 없다.
        Map<LocalDate, Long> departed = TransitionDays.departedByDate(days);

        List<TripDayResponse> responses = new ArrayList<>();
        for (TripDay day : days) {
            TravelPlace city = cities.get(day.getBasePlaceId());
            Long departedId = departed.get(day.getDayDate());
            TravelPlace from = departedId == null ? null : cities.get(departedId);
            responses.add(new TripDayResponse(
                    day.getId(),
                    trip.dayNumberOf(day.getDayDate()),
                    day.getDayDate(),
                    weekdayOf(day.getDayDate()),
                    legIndexes.getOrDefault(day.getDayDate(), 1),
                    departed.containsKey(day.getDayDate()),
                    day.getCityMemo(),
                    city == null ? null : BaseCityResponse.from(city),
                    from == null ? null : BaseCityResponse.from(from)));
        }
        return responses;
    }

    public List<CityLegResponse> cityLegs(Long memberId, Long tripId) {
        getOwned(memberId, tripId);
        List<TripDay> days = dayRepository.findAllByTripIdOrderByDayDateAsc(tripId);
        Map<Long, TravelPlace> cities = citiesOf(days);

        return LegDeriver.derive(days).stream()
                .map(leg -> toResponse(leg, cities.get(leg.basePlaceId())))
                .toList();
    }

    private static CityLegResponse toResponse(LegDeriver.DerivedLeg leg, TravelPlace city) {
        return new CityLegResponse(leg.legIndex(), leg.basePlaceId(),
                city == null ? null : cityName(city), leg.days(),
                leg.startDate(), leg.endDate(),
                city == null ? null : city.getTimezone(),
                city == null ? null : city.getLat(),
                city == null ? null : city.getLng());
    }

    private static String weekdayOf(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
    }

    private static String cityName(TravelPlace city) {
        return city.getCityName() != null ? city.getCityName() : city.getName();
    }

    /** 도시는 여러 날짜가 공유하므로 장소는 한 번씩만 읽는다. */
    private Map<Long, TravelPlace> citiesOf(List<TripDay> days) {
        if (days.isEmpty()) {
            return Map.of();
        }
        return placeRepository
                .findAllById(days.stream().map(TripDay::getBasePlaceId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(TravelPlace::getId, Function.identity()));
    }

    /** 날짜가 몇 번째 구간에 속하는지 — 구간 파생 결과를 날짜로 되풀어 둔다. */
    private static Map<LocalDate, Integer> legIndexByDate(List<TripDay> days) {
        Map<LocalDate, Integer> byDate = new HashMap<>();
        for (LegDeriver.DerivedLeg leg : LegDeriver.derive(days)) {
            for (LocalDate date = leg.startDate();
                    !date.isAfter(leg.endDate()); date = date.plusDays(1)) {
                byDate.put(date, leg.legIndex());
            }
        }
        return byDate;
    }

    private Trip getOwned(Long memberId, Long tripId) {
        // 소유권 실패도 404 — 403이면 "그 id의 여행은 존재한다"가 새어나간다.
        return tripRepository.findByIdAndMemberId(tripId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_TRIP_NOT_FOUND));
    }
}
