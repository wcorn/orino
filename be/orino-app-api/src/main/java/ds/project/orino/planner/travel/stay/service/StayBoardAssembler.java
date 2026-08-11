package ds.project.orino.planner.travel.stay.service;

import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.TripActivity;
import ds.project.orino.domain.planner.travel.entity.TripStay;
import ds.project.orino.domain.planner.travel.repository.TravelPlaceRepository;
import ds.project.orino.domain.planner.travel.repository.TripStayRepository;
import ds.project.orino.planner.travel.board.dto.BoardResponse;
import ds.project.orino.planner.travel.route.service.TravelTimeService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 보드에 붙는 숙소 — 날짜별 배지와 리스트 맨 아래 이동.
 *
 * <p>숙소는 <b>기간</b>을 갖고 날짜는 그 기간에서 파생한다. 어느 날짜에 어떤 숙소가 붙는지를
 * 저장하지 않는 이유는 여행 기간·숙소 기간 어느 쪽이 바뀌어도 저장된 배정이 곧 거짓이 되기
 * 때문이다.
 *
 * <pre>
 * stayTonight(day)  = checkIn &lt;= day &lt;  checkOut   오늘 밤 자는 곳
 * stayCheckout(day) = checkOut == day               오늘 체크아웃하는 곳
 * </pre>
 */
@Component
public class StayBoardAssembler {

    private final TripStayRepository stayRepository;
    private final TravelPlaceRepository placeRepository;
    private final TravelTimeService travelTimeService;

    public StayBoardAssembler(TripStayRepository stayRepository,
                              TravelPlaceRepository placeRepository,
                              TravelTimeService travelTimeService) {
        this.stayRepository = stayRepository;
        this.placeRepository = placeRepository;
        this.travelTimeService = travelTimeService;
    }

    /** 여행의 모든 숙소를 한 번에 읽어 둔다 — 날짜 탭마다 조회하면 기간만큼 쿼리가 는다. */
    public Stays of(Long tripId) {
        List<TripStay> stays = stayRepository.findAllByTripIdOrderByCheckInDateAscIdAsc(tripId);
        List<Long> placeIds = stays.stream()
                .map(TripStay::getPlaceId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, TravelPlace> places = placeIds.isEmpty() ? Map.of()
                : placeRepository.findAllByIdIn(placeIds).stream()
                        .collect(Collectors.toMap(TravelPlace::getId, Function.identity()));
        return new Stays(stays, places);
    }

    /**
     * 리스트 맨 아래 붙는 숙소 이동 — <b>그날 마지막 일정에서 오늘 밤 숙소까지</b>.
     *
     * <p>도시가 다르면 계산하지 않고 표시만 한다(§3.4). 오사카 가게에서 도쿄 숙소까지 "자동차
     * 6시간"이 뜨면 그 화면은 신뢰를 잃는다.
     *
     * <p><b>견주는 대상은 기준 도시가 아니라 출발지인 마지막 일정의 도시다.</b> 이 행이 답하는
     * 것은 "이 이동을 계산해도 되는가"이고, 경계는 그 이동의 양 끝에 있다 — 닛코 당일치기 날
     * (기준 도시 도쿄) 닛코에서 도쿄 숙소로 돌아가는 이동은 기준 도시와는 같지만 실제로는
     * 도시를 넘는다. 판정은 {@link TravelTimeService}가 한다. 출발지가 좌표 없는 일정이면 그
     * 앞 일정으로 밀리는데, 그 결정을 두 곳에서 따로 하면 어긋난다.
     *
     * <p><b>모르면 다르다고 하지 않는다</b>(D-23) — 도시 식별자가 한쪽이라도 없으면 같은 도시로
     * 보고 계산한다. 모르는 것을 근거로 기능을 끄면 사용자는 왜 시간이 안 나오는지 알 수 없다.
     */
    public BoardResponse.StayMove moveToStay(Stays stays, LocalDate date,
                                             List<TripActivity> ordered) {
        Optional<TripStay> tonight = stays.tonight(date);
        if (tonight.isEmpty() || ordered.isEmpty()) {
            return null;
        }
        TripStay stay = tonight.get();
        TravelPlace stayPlace = stays.placeOf(stay);
        if (stayPlace == null) {
            // 숙소에 장소가 안 붙어 있으면 좌표도 도시도 없다 — 행만 남긴다.
            return new BoardResponse.StayMove(stay.getId(), true, null, null);
        }
        return travelTimeService.moveToPlace(ordered, stayPlace)
                .map(move -> new BoardResponse.StayMove(stay.getId(), !move.crossCity(),
                        move.mode(), move.durationMinutes()))
                // 마지막 일정에 좌표가 없으면 이동 자체가 성립하지 않는다 — 행만 남긴다.
                .orElseGet(() -> new BoardResponse.StayMove(stay.getId(), true, null, null));
    }

    public BoardResponse.StayTonight tonight(Stays stays, LocalDate date, TravelPlace baseCity) {
        return stays.tonight(date)
                .map(stay -> new BoardResponse.StayTonight(stay.getId(), stay.getName(),
                        isSameCity(stays.placeOf(stay), baseCity),
                        stay.getCheckInTime(),
                        stay.getCheckInDate().equals(date)))
                .orElse(null);
    }

    public BoardResponse.StayCheckout checkout(Stays stays, LocalDate date) {
        return stays.checkout(date)
                .map(stay -> new BoardResponse.StayCheckout(stay.getId(), stay.getName(),
                        stay.getCheckOutTime()))
                .orElse(null);
    }

    /** 식별자가 둘 다 있고 서로 다를 때만 다른 도시로 본다(D-23). */
    private static boolean isSameCity(TravelPlace stayPlace, TravelPlace baseCity) {
        if (stayPlace == null || baseCity == null) {
            return true;
        }
        return !TravelPlace.crossesCity(stayPlace.getCityPlaceRef(), baseCity.getCityPlaceRef());
    }

    /** 여행 한 건의 숙소 전체와 그 장소들. 날짜 판정은 여기서 한다. */
    public record Stays(List<TripStay> all, Map<Long, TravelPlace> places) {

        public Optional<TripStay> tonight(LocalDate date) {
            return date == null ? Optional.empty()
                    : all.stream().filter(stay -> stay.coversNight(date)).findFirst();
        }

        public Optional<TripStay> checkout(LocalDate date) {
            return date == null ? Optional.empty()
                    : all.stream().filter(stay -> stay.isCheckOutOn(date)).findFirst();
        }

        TravelPlace placeOf(TripStay stay) {
            return stay.getPlaceId() == null ? null : places.get(stay.getPlaceId());
        }
    }
}
