package ds.project.orino.planner.travel.move.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.TravelMove;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripActivity;
import ds.project.orino.domain.planner.travel.entity.TripStay;
import ds.project.orino.domain.planner.travel.repository.TravelMoveRepository;
import ds.project.orino.domain.planner.travel.repository.TripActivityRepository;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.domain.planner.travel.repository.TripStayRepository;
import ds.project.orino.planner.travel.move.dto.MoveResponse;
import ds.project.orino.planner.travel.move.dto.MoveWriteRequest;
import ds.project.orino.planner.travel.push.service.NotificationScheduleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 이동 저장·삭제(#1208).
 *
 * <p><b>받기는 일정 id로, 저장하기는 장소 쌍으로.</b> 화면은 일정을 보고 있고 저장 단위는 장소
 * 쌍이라, 그 사이를 여기서 옮긴다. 화면이 장소 id를 들고 다니면 저장 단위를 바꿀 때마다 화면이
 * 함께 흔들린다.
 *
 * <p>저장·삭제 뒤에는 <b>그 날짜의 알림을 다시 짠다.</b> 출발 알림 시각이
 * {@code 시작시각 − 이동 − 5분}이라, 소요 시간을 고쳐 놓고 알림을 그대로 두면 예전 값으로 울린다.
 */
@Service
@Transactional
public class MoveWriteService {

    private final TripRepository tripRepository;
    private final TripActivityRepository activityRepository;
    private final TripStayRepository stayRepository;
    private final TravelMoveRepository moveRepository;
    private final NotificationScheduleService notificationService;

    public MoveWriteService(TripRepository tripRepository,
                            TripActivityRepository activityRepository,
                            TripStayRepository stayRepository,
                            TravelMoveRepository moveRepository,
                            NotificationScheduleService notificationService) {
        this.tripRepository = tripRepository;
        this.activityRepository = activityRepository;
        this.stayRepository = stayRepository;
        this.moveRepository = moveRepository;
        this.notificationService = notificationService;
    }

    /**
     * 이동을 저장한다. 같은 구간에 이미 값이 있으면 덮어쓴다 — 한 구간에 이동은 하나다.
     *
     * <p>같은 장소 쌍을 잇는 다른 날의 이동도 함께 바뀐다. 그게 장소 쌍에 저장하는 이유다 —
     * 도쿄역에서 숙소까지를 한 번 적으면 매일 다시 적지 않아도 된다.
     */
    public MoveResponse save(Long memberId, Long tripId, MoveWriteRequest request) {
        if (!request.hasExactlyOneDestination()) {
            throw new CustomException(ErrorCode.TRAVEL_MOVE_NOT_AVAILABLE);
        }
        Trip trip = getOwnedTrip(memberId, tripId);
        TripActivity from = getActivity(tripId, request.fromActivityId());
        Long fromPlaceId = placeIdOf(from);
        Long toPlaceId = request.toActivityId() != null
                ? placeIdOf(getActivity(tripId, request.toActivityId()))
                : placeIdOf(getStay(tripId, request.toStayId()));

        // 자기 자신으로 가는 이동은 이동이 아니다. 저장하면 화면이 "이미 그곳인데 이동하라"를
        // 그리게 되고, 그 행은 어떤 값을 넣어도 틀린 행이다.
        if (fromPlaceId.equals(toPlaceId)) {
            throw new CustomException(ErrorCode.TRAVEL_MOVE_NOT_AVAILABLE);
        }

        TravelMove move = moveRepository
                .findByMemberIdAndFromPlaceIdAndToPlaceId(memberId, fromPlaceId, toPlaceId)
                .orElseGet(() -> new TravelMove(memberId, fromPlaceId, toPlaceId, request.mode()));
        move.update(request.mode(), blankToNull(request.name()), request.durationMinutes(),
                blankToNull(request.url()), blankToNull(request.memo()));
        TravelMove saved = moveRepository.save(move);

        rescheduleFor(trip, from.getActivityDate());
        return request.toActivityId() != null
                ? MoveResponse.between(from.getId(), request.toActivityId(), saved)
                : MoveResponse.toStay(from.getId(), request.toStayId(), saved);
    }

    /**
     * 이동을 지운다. 없는 이동을 지워도 성공이다 — 두 번 눌렀을 때 두 번째만 실패하면
     * 화면은 지워지지 않았다고 읽는다.
     */
    public void delete(Long memberId, Long tripId, Long fromActivityId,
                       Long toActivityId, Long toStayId) {
        if ((toActivityId == null) == (toStayId == null)) {
            throw new CustomException(ErrorCode.TRAVEL_MOVE_NOT_AVAILABLE);
        }
        Trip trip = getOwnedTrip(memberId, tripId);
        TripActivity from = getActivity(tripId, fromActivityId);
        Long fromPlaceId = placeIdOf(from);
        Long toPlaceId = toActivityId != null
                ? placeIdOf(getActivity(tripId, toActivityId))
                : placeIdOf(getStay(tripId, toStayId));

        moveRepository.deleteByMemberIdAndFromPlaceIdAndToPlaceId(memberId, fromPlaceId, toPlaceId);
        // 이동이 사라지면 출발 알림도 설 자리가 없다 — 지워진 값으로 울리게 두지 않는다.
        rescheduleFor(trip, from.getActivityDate());
    }

    private void rescheduleFor(Trip trip, LocalDate date) {
        if (date != null) {
            notificationService.rescheduleDate(trip.getId(), date);
        }
    }

    /** 장소가 없는 일정은 이동의 끝이 될 수 없다 — 어디서 어디로인지가 없다. */
    private static Long placeIdOf(TripActivity activity) {
        if (activity.getPlaceId() == null) {
            throw new CustomException(ErrorCode.TRAVEL_MOVE_NOT_AVAILABLE);
        }
        return activity.getPlaceId();
    }

    private static Long placeIdOf(TripStay stay) {
        if (stay.getPlaceId() == null) {
            throw new CustomException(ErrorCode.TRAVEL_MOVE_NOT_AVAILABLE);
        }
        return stay.getPlaceId();
    }

    private Trip getOwnedTrip(Long memberId, Long tripId) {
        return tripRepository.findByIdAndMemberId(tripId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_TRIP_NOT_FOUND));
    }

    private TripActivity getActivity(Long tripId, Long activityId) {
        return activityRepository.findByIdAndTripId(activityId, tripId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_ACTIVITY_NOT_FOUND));
    }

    private TripStay getStay(Long tripId, Long stayId) {
        return stayRepository.findByIdAndTripId(stayId, tripId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_STAY_NOT_FOUND));
    }

    /**
     * 빈 문자열은 null로 눕힌다. 입력창을 비워 저장한 것은 "지웠다"는 뜻인데, 빈 문자열로
     * 남으면 {@code name}이 있는 것처럼 읽혀 화면에 빈 칸이 그려진다.
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
