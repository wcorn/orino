package ds.project.orino.planner.travel.photo.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripActivity;
import ds.project.orino.domain.planner.travel.entity.TripActivityLog;
import ds.project.orino.domain.planner.travel.entity.TripActivityPhoto;
import ds.project.orino.domain.planner.travel.repository.TripActivityLogRepository;
import ds.project.orino.domain.planner.travel.repository.TripActivityPhotoRepository;
import ds.project.orino.domain.planner.travel.repository.TripActivityRepository;
import ds.project.orino.domain.planner.travel.repository.TripRepository;
import ds.project.orino.planner.travel.photo.dto.PhotoRegisterRequest;
import ds.project.orino.planner.travel.photo.dto.PhotoResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 기록 사진의 메타 등록·조회·삭제(§S-07).
 *
 * <p><b>바이트는 여기를 지나가지 않는다.</b> 브라우저가 presigned URL로 MinIO에 직접 올리고,
 * 이 서비스는 끝난 것만 받아 적는다. 그래서 "업로드 실패"는 서버가 모르는 사건이고, FE가
 * 성공한 장만 골라 보낸다.
 *
 * <p>사진은 일정이 아니라 <b>기록</b>({@link TripActivityLog})에 매달린다. 사진만 올리는
 * 경우를 위해 기록이 없으면 빈 기록을 만든다 — 사진 열 장을 올렸는데 저장할 곳이 없다고
 * 거절하는 것보다 낫다.
 */
@Service
@Transactional(readOnly = true)
public class TravelPhotoService {

    private final TripActivityRepository activityRepository;
    private final TripActivityLogRepository logRepository;
    private final TripActivityPhotoRepository photoRepository;
    private final TripRepository tripRepository;
    private final TravelPhotoStorageService storageService;
    private final Clock clock;

    public TravelPhotoService(TripActivityRepository activityRepository,
                              TripActivityLogRepository logRepository,
                              TripActivityPhotoRepository photoRepository,
                              TripRepository tripRepository,
                              TravelPhotoStorageService storageService,
                              Clock clock) {
        this.activityRepository = activityRepository;
        this.logRepository = logRepository;
        this.photoRepository = photoRepository;
        this.tripRepository = tripRepository;
        this.storageService = storageService;
        this.clock = clock;
    }

    /**
     * 업로드 URL 발급 전에도 소유권과 시작일을 본다 — 남의 일정 경로에 파일을 올릴 수 없어야
     * 하고, 시작 전이라 등록이 거부될 사진을 굳이 올리게 하지도 않는다.
     */
    public void requireUploadable(Long memberId, Long activityId) {
        requireTripStarted(getOwnedActivity(memberId, activityId));
    }

    @Transactional
    public List<PhotoResponse> register(Long memberId, Long activityId,
                                        PhotoRegisterRequest request) {
        TripActivity activity = getOwnedActivity(memberId, activityId);
        requireTripStarted(activity);
        TripActivityLog log = logRepository.findByActivityId(activity.getId())
                // 평점·메모 없이 사진만 올리는 경우. 빈 기록이지만 사진이 붙으므로 빈 게 아니다.
                .orElseGet(() -> logRepository.save(
                        new TripActivityLog(activity.getId(), null, null)));

        long existing = photoRepository.countByLogId(log.getId());
        if (existing + request.photos().size() > TripActivityPhoto.MAX_PHOTOS) {
            throw new CustomException(ErrorCode.TRAVEL_PHOTO_LIMIT_EXCEEDED);
        }

        int order = photoRepository.nextSortOrder(log.getId());
        for (PhotoRegisterRequest.Photo photo : request.photos()) {
            photoRepository.save(new TripActivityPhoto(log.getId(), photo.objectKey(),
                    photo.thumbKey(), photo.width(), photo.height(), order++));
        }
        return photosOf(log.getId());
    }

    /**
     * 사진 행을 지우고 오브젝트도 지운다.
     *
     * <p>오브젝트 삭제는 best-effort다 — 실패해도 행은 지운다. 반대로 하면 화면에서 지운
     * 사진이 새로고침에 되살아난다.
     */
    @Transactional
    public void delete(Long memberId, Long photoId) {
        TripActivityPhoto photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_PHOTO_NOT_FOUND));
        TripActivityLog log = logRepository.findById(photo.getLogId())
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_PHOTO_NOT_FOUND));
        // 남의 사진도 404다 — 403이면 그 id의 사진이 존재한다는 사실이 새어나간다.
        getOwnedActivity(memberId, log.getActivityId());

        photoRepository.delete(photo);
        storageService.deleteObjects(List.of(
                photo.getObjectKey(),
                photo.getThumbKey() == null ? "" : photo.getThumbKey()));
    }

    /** 한 기록의 사진. 업로드 순서 그대로다. */
    public List<PhotoResponse> photosOf(Long logId) {
        return toResponses(photoRepository.findAllByLogIdOrderBySortOrderAscIdAsc(logId));
    }

    /**
     * 여러 기록의 사진을 한 번에 읽어 기록별로 묶는다 — 보드가 기록 수만큼 쿼리를 날리지
     * 않게 한다.
     */
    public Map<Long, List<PhotoResponse>> photosByLog(List<Long> logIds) {
        if (logIds.isEmpty()) {
            return Map.of();
        }
        return photoRepository.findAllByLogIdInOrderBySortOrderAscIdAsc(logIds).stream()
                .collect(Collectors.groupingBy(TripActivityPhoto::getLogId,
                        Collectors.collectingAndThen(Collectors.toList(), this::toResponses)));
    }

    private List<PhotoResponse> toResponses(List<TripActivityPhoto> photos) {
        return photos.stream()
                .map(photo -> PhotoResponse.of(photo,
                        storageService.toPublicUrl(photo.getObjectKey()),
                        storageService.toPublicUrl(photo.getThumbKey())))
                .toList();
    }

    /**
     * 일정 경로에는 {@code tripId}가 없으므로 여행을 거쳐 소유권을 확인한다.
     * 남의 일정도 404다.
     */
    /**
     * 기록은 여행 시작일부터다(§S-07) — 사진도 기록의 일부라 같은 규칙을 따른다.
     * 기준은 기기 시간대가 아니라 여행 타임존의 오늘이다.
     */
    private void requireTripStarted(TripActivity activity) {
        Trip trip = tripRepository.findById(activity.getTripId()).orElseThrow();
        if (trip.todayAtDestination(clock).isBefore(trip.getStartDate())) {
            throw new CustomException(ErrorCode.TRAVEL_LOG_BEFORE_TRIP);
        }
    }

    private TripActivity getOwnedActivity(Long memberId, Long activityId) {
        TripActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAVEL_ACTIVITY_NOT_FOUND));
        boolean owned = tripRepository.findByIdAndMemberId(activity.getTripId(), memberId)
                .isPresent();
        if (!owned) {
            throw new CustomException(ErrorCode.TRAVEL_ACTIVITY_NOT_FOUND);
        }
        return activity;
    }
}
