package ds.project.orino.planner.travel.stay.service;

import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.TripActivity;
import ds.project.orino.domain.planner.travel.entity.TripStay;
import ds.project.orino.domain.planner.travel.repository.TravelPlaceRepository;
import ds.project.orino.domain.planner.travel.repository.TripStayRepository;
import ds.project.orino.planner.travel.board.dto.BoardResponse;
import ds.project.orino.planner.travel.move.dto.MoveResponse;
import ds.project.orino.planner.travel.move.service.MoveService;
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
    private final MoveService moveService;

    public StayBoardAssembler(TripStayRepository stayRepository,
                              TravelPlaceRepository placeRepository,
                              MoveService moveService) {
        this.stayRepository = stayRepository;
        this.placeRepository = placeRepository;
        this.moveService = moveService;
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
     * <p>일정 사이 이동과 <b>같은 저장소</b>를 탄다(#1208). 장소 쌍이 키라 숙소든 일정이든 두
     * 장소를 잇는 이동은 한 값이다 — 도쿄역에서 숙소까지를 한 번 적으면 매일 다시 적지 않아도
     * 된다.
     *
     * <p>도시 경계 조건은 없다. 예전에는 도시가 다르면 계산하지 않았지만, 지금은 사용자가
     * 적어 두면 그대로 실린다 — 오사카 가게에서 도쿄 숙소까지 신칸센 2시간 30분은 사용자가
     * 아는 값이고, 앱이 몰라서 비워 둘 이유가 없다.
     *
     * <p>출발지 판정은 {@link MoveService}가 한다. 장소 없는 일정이면 그 앞 일정으로 밀리는데,
     * 그 결정을 두 곳에서 따로 하면 어긋난다.
     */
    public MoveResponse moveToStay(Long memberId, Stays stays, LocalDate date,
                                   List<TripActivity> ordered) {
        Optional<TripStay> tonight = stays.tonight(date);
        if (tonight.isEmpty() || ordered.isEmpty()) {
            return null;
        }
        TripStay stay = tonight.get();
        TravelPlace stayPlace = stays.placeOf(stay);
        // 마지막 일정이 이미 그 숙소면 이동이 없다 — 행도 없다. 억지로 그리면 "이미 그곳인데
        // 이동하라"가 된다.
        if (stayPlace != null && moveService.alreadyAt(ordered, stayPlace.getId())) {
            return null;
        }
        // 숙소에 장소가 안 붙어 있으면 이동의 도착지가 없다 — 적을 수도 없으므로 행도 없다.
        if (stayPlace == null) {
            return null;
        }
        return moveService.moveToPlace(memberId, ordered, stay.getId(), stayPlace.getId())
                // 마지막 일정에 장소가 없으면 이동 자체가 성립하지 않는다.
                .orElse(null);
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
