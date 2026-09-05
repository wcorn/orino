package ds.project.orino.planner.travel.day.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripDay;
import ds.project.orino.domain.planner.travel.repository.TravelPlaceRepository;
import ds.project.orino.domain.planner.travel.repository.TripDayRepository;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.day.dto.DayUpdateRequest;
import ds.project.orino.planner.travel.day.dto.TripDayResponse;
import ds.project.orino.planner.travel.place.service.PlaceService;
import ds.project.orino.planner.travel.push.service.NotificationScheduleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 날짜의 기준 도시를 바꾸고, 그 변경이 부르는 <b>연쇄를 한 곳에서</b> 처리한다.
 *
 * <pre>
 * 기준 도시 변경
 *   → 타임존(벽시계 값은 그대로, 발송 시각만 재계산)
 *   → 통화 · 날씨 · 검색 편향 좌표
 * </pre>
 *
 * <p><b>연쇄를 흩어 두면 하나가 빠졌을 때 화면은 새 도시를 보여주는데 알림은 옛 시각에 온다</b> —
 * 사용자가 알아차릴 방법이 없는 오류다. 그래서 도시를 바꾸는 입구를 여기 하나로 둔다.
 *
 * <p>통화·날씨·검색 좌표는 <b>따로 갱신할 것이 없다.</b> 셋 다 저장하지 않고 조회할 때 기준
 * 도시에서 파생하므로, 도시가 바뀐 다음 조회부터 새 값이 나온다. 이동시간 캐시도 마찬가지로
 * 좌표 쌍이 키라(§4.4) 도시가 바뀌어도 두 장소 사이 거리는 그대로다 — 도시 경계를 넘는
 * 구간을 계산 대상에서 빼는 규칙은 3단계에서 붙는다.
 *
 * <p><b>이미 담긴 일정의 장소는 건드리지 않고 경고도 띄우지 않는다.</b> 오사카 가게를 교토
 * 날짜에 두는 건 사용자의 선택이다.
 */
@Service
public class BaseCityChangeService {

    private final TripRepository tripRepository;
    private final TripDayRepository dayRepository;
    private final TravelPlaceRepository placeRepository;
    private final PlaceService placeService;
    private final NotificationScheduleService notificationService;
    private final TripDayQueryService queryService;

    public BaseCityChangeService(TripRepository tripRepository,
                                 TripDayRepository dayRepository,
                                 TravelPlaceRepository placeRepository,
                                 PlaceService placeService,
                                 NotificationScheduleService notificationService,
                                 TripDayQueryService queryService) {
        this.tripRepository = tripRepository;
        this.dayRepository = dayRepository;
        this.placeRepository = placeRepository;
        this.placeService = placeService;
        this.notificationService = notificationService;
        this.queryService = queryService;
    }

    /**
     * 보낸 필드만 반영한다. 메모만 고치려는 요청이 기준 도시를 건드리면 안 되고, 그 반대도
     * 마찬가지다.
     *
     * <p>응답은 바꾼 날짜 하나가 아니라 <b>기간 전체의 날짜</b>다. 하루를 바꾸면 구간이 다시
     * 나뉘어({@code legIndex}·{@code cityChanged}) 앞뒤 날짜의 표시까지 달라지므로, 한 건만
     * 돌려주면 화면이 곧바로 목록을 다시 받아야 한다.
     */
    @Transactional
    public List<TripDayResponse> update(Long memberId, Long dayId, DayUpdateRequest request) {
        TripDay day = dayRepository.findById(dayId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_DAY_NOT_FOUND));
        Trip trip = tripRepository.findByIdAndMemberId(day.getTripId(), memberId)
                // 남의 여행 날짜도 404 — 존재 여부가 새어나가지 않는다.
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_DAY_NOT_FOUND));

        // 검색 결과로 바꾸는 경우, 담긴 도시의 id는 해석한 뒤에야 안다 — 이미 이 여행이 쓰던
        // 도시를 다시 고른 것일 수도 있어 "바뀌었나"는 그다음에 묻는다.
        Long newCityId = request.hasCity() ? resolveCity(memberId, request) : null;
        boolean cityChanged = newCityId != null && !newCityId.equals(day.getBasePlaceId());
        if (cityChanged) {
            day.changeBaseCity(newCityId);
        }
        if (request.cityMemo() != null) {
            day.updateCityMemo(request.cityMemo());
        }
        dayRepository.saveAndFlush(day);

        if (cityChanged) {
            // 벽시계 시각은 그대로다. 그 시각이 어느 순간인지가 바뀌었을 뿐이라 발송 시각만
            // 다시 잡는다(§4.2). 일정 알림은 그 날짜만 영향을 받는다.
            notificationService.rescheduleDate(trip.getId(), day.getDayDate());
            // 아침 요약은 다르다 — 일정이 아니라 날짜에 걸려 있고, "도시가 바뀌는 날인가"가
            // 다음 날짜의 발송 시각까지 뒤집는다(v2.1 §3.6). 위 호출은 요약을 건드리지 않는다.
            notificationService.rescheduleMorningSummary(trip.getId(), day.getDayDate());
            // 준비 알림도 어느 그물에도 안 걸린다 — 일정에 매달려 있지 않고, 가는 날짜가
            // 여행 기간 밖(출발 전날)이라 날짜로 찾는 경로에도 없다. 첫날 도시를 바꾸면
            // 「출발 전날 09:00」이 가리키는 순간이 통째로 달라진다(v2.2 §14).
            notificationService.reschedulePrepReminder(trip.getId(), day.getDayDate());
        }
        return queryService.days(memberId, trip.getId());
    }

    /**
     * 고른 방식 그대로 도시를 해석한다 — 담아 둔 도시면 id로, 검색 결과면 담으면서.
     *
     * <p>검색 결과를 그대로 받는 이유는 구간 입력과 같다: 고르기 전에 저장부터 하라고 하면
     * 저장했다가 취소한 도시가 쌓이고, 화면이 만든 도시에는 <b>도시 식별자가 없어</b> 그날
     * 일정이 전부 "다른 도시"로 표시된다.
     */
    private Long resolveCity(Long memberId, DayUpdateRequest request) {
        if (request.hasGoogleId()) {
            return requireCity(placeService
                    .upsertCityFromGoogle(memberId, request.baseCityGooglePlaceId().trim()))
                    .getId();
        }
        return requireCity(placeRepository
                .findByIdAndMemberId(request.baseCityPlaceId(), memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_PLACE_NOT_FOUND)))
                .getId();
    }

    /**
     * 도시가 아닌 장소는 기준 도시가 될 수 없다. 타임존은 우연히 맞더라도 도시 일치 판정이
     * 그날 일정을 전부 "다른 도시"로 만든다.
     */
    private TravelPlace requireCity(TravelPlace place) {
        if (!place.isCity() || place.getTimezone() == null) {
            throw new CustomException(ErrorCode.TRAVEL_NOT_A_CITY);
        }
        return place;
    }
}
