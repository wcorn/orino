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
import java.time.ZoneId;

@Service
public class ReviewCompletionService {

    private final ReviewScheduleRepository reviewScheduleRepository;
    private final Clock clock;

    public ReviewCompletionService(ReviewScheduleRepository reviewScheduleRepository, Clock clock) {
        this.reviewScheduleRepository = reviewScheduleRepository;
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

        return new ReviewCompletionResponse(
                CompletedReviewView.of(current),
                ReviewScheduleView.firstReview(next));
    }
}
