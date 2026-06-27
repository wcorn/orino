package ds.project.orino.planner.review.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.planner.review.dto.CompletedReviewView;
import ds.project.orino.planner.review.dto.ReviewCompletionRequest;
import ds.project.orino.planner.review.dto.ReviewCompletionResponse;
import ds.project.orino.planner.review.dto.ReviewScheduleView;
import ds.project.orino.core.time.UserTimeZone;
import ds.project.orino.planner.review.sm2.Sm2Calculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class ReviewCompletionService {

    private final ReviewScheduleRepository reviewScheduleRepository;
    private final ReviewMirrorService reviewMirrorService;
    private final Clock clock;

    public ReviewCompletionService(ReviewScheduleRepository reviewScheduleRepository,
                                   ReviewMirrorService reviewMirrorService, Clock clock) {
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.reviewMirrorService = reviewMirrorService;
        this.clock = clock;
    }

    @Transactional
    public ReviewCompletionResponse complete(Long memberId, Long reviewId, ReviewCompletionRequest request) {
        ReviewSchedule current = reviewScheduleRepository.findByIdAndMemberId(reviewId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        if (current.getStatus() != ReviewStatus.PENDING) {
            throw new CustomException(ErrorCode.INVALID_STATE);
        }

        Instant now = clock.instant();
        ZoneId zone = UserTimeZone.get();

        int newSequence = current.getSequence() + 1;
        Sm2Calculator.Result computed = Sm2Calculator.next(
                newSequence, current.getIntervalDays(), current.getEaseFactor(), request.rating());

        current.complete(request.rating(), now, zone);

        Instant scheduledAt = ReviewSchedule.computeScheduledAt(
                request.rating(), computed.intervalDays(), now, zone);
        ReviewSchedule next = reviewScheduleRepository.save(new ReviewSchedule(
                memberId, current.getFlashcardId(), newSequence,
                scheduledAt, computed.intervalDays(), computed.easeFactor()));

        // 완료된 dueDate(감소)와 다음 dueDate(증가) 묶음을 모두 재동기화(커밋 후, 미러 활성 시에만).
        // AGAIN은 04:00 정각이 아니어서 reconcile 집계에서 자연히 제외된다.
        LocalDate completedDate = current.getScheduledAt().atZone(zone).toLocalDate();
        LocalDate nextDate = next.getScheduledAt().atZone(zone).toLocalDate();
        reviewMirrorService.reconcileAfterCommit(memberId, List.of(completedDate, nextDate), zone);

        return new ReviewCompletionResponse(
                CompletedReviewView.of(current),
                ReviewScheduleView.firstReview(next));
    }
}
