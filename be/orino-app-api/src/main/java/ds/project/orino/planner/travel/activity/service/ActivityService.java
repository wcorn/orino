package ds.project.orino.planner.travel.activity.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripActivity;
import ds.project.orino.domain.planner.travel.repository.TravelPlaceRepository;
import ds.project.orino.domain.planner.travel.repository.TripActivityRepository;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.activity.dto.ActivityPlace;
import ds.project.orino.planner.travel.activity.dto.ActivityResponse;
import ds.project.orino.planner.travel.activity.dto.ActivityWriteRequest;
import ds.project.orino.planner.travel.activity.dto.ReorderRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 일정 CRUD와 드래그 결과 반영.
 *
 * <p>정렬 기준은 {@code sortOrder}뿐이다 — 시각으로 정렬하지 않는다. 시각 없는 일정이 정상이고,
 * 사용자가 드래그로 정한 순서가 시각보다 우선하기 때문이다.
 *
 * <p>{@code activityDate}가 {@code null}이면 미배정 보관함이다. 보관함도 자기 {@code sortOrder}
 * 열을 가진다.
 */
@Service
@Transactional(readOnly = true)
public class ActivityService {

    /**
     * 순서 변경에서 "보내지 않은 일정"을 임시로 밀어 둘 구간. 보낸 일정(0..n-1)과 절대 겹치지
     * 않을 만큼 크면 되고, 정규화가 끝나면 다시 0..n-1로 돌아오므로 저장되지는 않는다.
     */
    private static final int UNLISTED_BAND = 1_000_000;

    private final TripActivityRepository activityRepository;
    private final TripRepository tripRepository;
    private final TravelPlaceRepository placeRepository;

    public ActivityService(TripActivityRepository activityRepository,
                           TripRepository tripRepository,
                           TravelPlaceRepository placeRepository) {
        this.activityRepository = activityRepository;
        this.tripRepository = tripRepository;
        this.placeRepository = placeRepository;
    }

    /** 새 일정은 해당 날짜(또는 보관함)의 맨 뒤에 붙인다. 클라이언트가 순서를 정하지 않는다. */
    @Transactional
    public ActivityResponse create(Long memberId, Long tripId, ActivityWriteRequest request) {
        Trip trip = getOwnedTrip(memberId, tripId);
        requireDateWithinTrip(trip, request.activityDate());

        int sortOrder = activityRepository.nextSortOrder(tripId, request.activityDate());
        TripActivity activity = new TripActivity(tripId, request.title().trim(),
                request.activityDate(), sortOrder, request.startTime());
        // 생성자가 안 받는 나머지 계획 필드(메모·링크·장소·알림)를 이어서 채운다.
        activity.update(request.title().trim(), request.startTime(), request.memo(), request.url());
        activity.updatePlace(request.placeId());
        activity.updateNotification(request.notifyEnabledOrDefault(), request.notifyMinutes(),
                request.departureNotifyEnabledOrDefault());

        return toResponse(activityRepository.save(activity));
    }

    public ActivityResponse detail(Long memberId, Long activityId) {
        return toResponse(getOwnedActivity(memberId, activityId));
    }

    /**
     * 계획 영역 전체 수정. 날짜가 바뀌면 옮겨간 날짜의 맨 뒤로 붙인다 — 정확한 위치는
     * 드래그(`/activities/order`)가 정하는 것이고, 여기서는 순서를 추측하지 않는다.
     */
    @Transactional
    public ActivityResponse update(Long memberId, Long activityId, ActivityWriteRequest request) {
        TripActivity activity = getOwnedActivity(memberId, activityId);
        Trip trip = getTripOf(activity);
        requireDateWithinTrip(trip, request.activityDate());

        if (!Objects.equals(activity.getActivityDate(), request.activityDate())) {
            LocalDate previousDate = activity.getActivityDate();
            activity.moveTo(request.activityDate(),
                    activityRepository.nextSortOrder(activity.getTripId(), request.activityDate()));
            // 떠나온 날짜에 순서 구멍이 남는다. 0..n-1로 메워 다음 드래그가 어긋나지 않게 한다.
            reindex(activity.getTripId(), previousDate);
        }
        activity.update(request.title().trim(), request.startTime(), request.memo(), request.url());
        activity.updatePlace(request.placeId());
        activity.updateNotification(request.notifyEnabledOrDefault(), request.notifyMinutes(),
                request.departureNotifyEnabledOrDefault());

        return toResponse(activity);
    }

    @Transactional
    public void delete(Long memberId, Long activityId) {
        TripActivity activity = getOwnedActivity(memberId, activityId);
        LocalDate date = activity.getActivityDate();
        Long tripId = activity.getTripId();

        activityRepository.delete(activity);
        activityRepository.flush();
        reindex(tripId, date);
    }

    /**
     * 드래그 결과를 한 번에 반영한다 — 순서 변경과 날짜 이동이 같은 요청이고 한 트랜잭션이다.
     *
     * <p>보낸 배열 순서대로 0..n-1을 부여한 뒤, <b>건드린 날짜 전체를 다시 0..n-1로 정규화한다.</b>
     * 클라이언트가 실수로 일부만 보내도 순서에 구멍이나 중복이 남지 않게 하기 위한 것이다.
     */
    @Transactional
    public void reorder(Long memberId, Long tripId, ReorderRequest request) {
        Trip trip = getOwnedTrip(memberId, tripId);

        Map<Long, TripActivity> targets = loadOwnedActivities(tripId, request);
        // 옮기기 전 날짜도 정규화 대상이다 — 일정이 빠져나간 날짜에 구멍이 남는다.
        Set<LocalDate> touchedDates = new LinkedHashSet<>();
        targets.values().forEach(activity -> touchedDates.add(activity.getActivityDate()));

        for (ReorderRequest.Move move : request.moves()) {
            requireDateWithinTrip(trip, move.date());
            touchedDates.add(move.date());
            pushUnlistedBack(tripId, move.date(), Set.copyOf(move.activityIds()));

            int order = 0;
            for (Long activityId : move.activityIds()) {
                targets.get(activityId).moveTo(move.date(), order++);
            }
        }
        activityRepository.flush();
        touchedDates.forEach(date -> reindex(tripId, date));
    }

    // ---------------- helpers ----------------

    /**
     * 요청에 담긴 일정을 전부 읽어 이 여행 소유인지 확인한다. 하나라도 남의 것이면 통째로 막는다 —
     * 일부만 반영되면 화면 순서와 서버 순서가 어긋난 채로 남는다.
     */
    private Map<Long, TripActivity> loadOwnedActivities(Long tripId, ReorderRequest request) {
        List<Long> ids = request.moves().stream()
                .flatMap(move -> move.activityIds().stream())
                .distinct()
                .toList();
        Map<Long, TripActivity> found = activityRepository.findAllById(ids).stream()
                .filter(activity -> activity.getTripId().equals(tripId))
                .collect(Collectors.toMap(TripActivity::getId, activity -> activity));
        if (found.size() != ids.size()) {
            throw new CustomException(ErrorCode.TRAVEL_ACTIVITY_NOT_FOUND);
        }
        return found;
    }

    /**
     * 보낸 목록에 없는데 그 날짜에 남아 있는 일정을 뒤 구간으로 밀어 둔다.
     *
     * <p>이게 없으면 보낸 일정과 안 보낸 일정의 {@code sortOrder}가 겹쳐, 뒤이은 정규화에서
     * id 순으로 뒤섞인다. 클라이언트가 "이 날짜는 이 순서로 시작한다"고 말한 이상 보낸 쪽이 앞선다.
     */
    private void pushUnlistedBack(Long tripId, LocalDate date, Set<Long> listedIds) {
        activitiesOn(tripId, date).stream()
                .filter(activity -> !listedIds.contains(activity.getId()))
                .forEach(activity -> activity.reorderTo(UNLISTED_BAND + activity.getSortOrder()));
    }

    private List<TripActivity> activitiesOn(Long tripId, LocalDate date) {
        return date == null
                ? activityRepository.findUnscheduled(tripId)
                : activityRepository.findAllByTripIdAndActivityDateOrderBySortOrderAscIdAsc(tripId, date);
    }

    /** 한 날짜(또는 보관함)의 순서를 현재 순서를 유지한 채 0..n-1로 다시 매긴다. */
    private void reindex(Long tripId, LocalDate date) {
        List<TripActivity> ordered = activitiesOn(tripId, date);
        int order = 0;
        for (TripActivity activity : ordered) {
            activity.reorderTo(order++);
        }
    }

    private Trip getOwnedTrip(Long memberId, Long tripId) {
        return tripRepository.findByIdAndMemberId(tripId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_TRIP_NOT_FOUND));
    }

    /**
     * 일정 경로에는 {@code tripId}가 없으므로 여행을 거쳐 소유권을 확인한다.
     * 남의 일정도 404다 — 403이면 그 id의 일정이 존재한다는 사실이 새어나간다.
     */
    private TripActivity getOwnedActivity(Long memberId, Long activityId) {
        TripActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_ACTIVITY_NOT_FOUND));
        boolean owned = tripRepository.findByIdAndMemberId(activity.getTripId(), memberId).isPresent();
        if (!owned) {
            throw new CustomException(ErrorCode.TRAVEL_ACTIVITY_NOT_FOUND);
        }
        return activity;
    }

    private Trip getTripOf(TripActivity activity) {
        return tripRepository.findById(activity.getTripId()).orElseThrow();
    }

    /** 보관함(null)은 언제나 허용, 날짜가 있으면 여행 기간 안이어야 한다. */
    private void requireDateWithinTrip(Trip trip, LocalDate date) {
        if (date != null && !trip.covers(date)) {
            throw new CustomException(ErrorCode.TRAVEL_DATE_OUT_OF_RANGE);
        }
    }

    private ActivityResponse toResponse(TripActivity activity) {
        return ActivityResponse.of(activity, placeOf(activity.getPlaceId()));
    }

    private ActivityPlace placeOf(Long placeId) {
        if (placeId == null) {
            return null;
        }
        return placeRepository.findById(placeId).map(ActivityPlace::from).orElse(null);
    }

    /** 여러 일정의 장소를 한 번에 붙인다(보드가 N+1로 장소를 읽지 않게). */
    public List<ActivityResponse> toResponses(List<TripActivity> activities) {
        List<Long> placeIds = activities.stream()
                .map(TripActivity::getPlaceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ActivityPlace> places = placeIds.isEmpty() ? Map.of()
                : placeRepository.findAllByIdIn(placeIds).stream()
                        .collect(Collectors.toMap(TravelPlace::getId, ActivityPlace::from));

        List<ActivityResponse> responses = new ArrayList<>();
        for (TripActivity activity : activities) {
            // placeId가 null인 일정이 대부분이다. Map.of()는 get(null)에 NPE를 던지므로 먼저 거른다.
            ActivityPlace place = activity.getPlaceId() == null
                    ? null : places.get(activity.getPlaceId());
            responses.add(ActivityResponse.of(activity, place));
        }
        return responses;
    }
}
