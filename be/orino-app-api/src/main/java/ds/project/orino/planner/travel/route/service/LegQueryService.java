package ds.project.orino.planner.travel.route.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.TripActivity;
import ds.project.orino.domain.planner.travel.repository.TripActivityRepository;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.route.client.TravelMode;
import ds.project.orino.planner.travel.route.dto.LegResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 이동수단 시트의 단건 구간 조회. 소유권 확인과 "정말 인접한 두 일정인가"를 여기서 본다.
 */
@Service
@Transactional(readOnly = true)
public class LegQueryService {

    private final TripRepository tripRepository;
    private final TripActivityRepository activityRepository;
    private final LegService legService;

    public LegQueryService(TripRepository tripRepository,
                           TripActivityRepository activityRepository,
                           LegService legService) {
        this.tripRepository = tripRepository;
        this.activityRepository = activityRepository;
        this.legService = legService;
    }

    public LegResponse leg(Long memberId, Long tripId, Long fromActivityId,
                           Long toActivityId, TravelMode mode) {
        tripRepository.findByIdAndMemberId(tripId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_TRIP_NOT_FOUND));

        TripActivity from = activityRepository.findByIdAndTripId(fromActivityId, tripId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_ACTIVITY_NOT_FOUND));
        LocalDate date = from.getActivityDate();
        if (date == null) {
            // 보관함은 날짜에 배정되지 않아 순서에 이동 의미가 없다.
            throw new CustomException(ErrorCode.TRAVEL_LEG_NOT_AVAILABLE);
        }

        // 그 날짜의 정렬된 목록을 그대로 넘긴다 — 사이에 장소 없는 일정을 건너뛰는 규칙이
        // 보드와 어긋나면, 시트가 화면에 없는 구간을 계산하게 된다.
        List<TripActivity> ordered = activityRepository
                .findAllByTripIdAndActivityDateOrderBySortOrderAscIdAsc(tripId, date);
        return legService.legBetween(ordered, fromActivityId, toActivityId, mode);
    }
}
