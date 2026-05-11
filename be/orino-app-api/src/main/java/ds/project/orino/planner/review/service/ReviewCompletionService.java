package ds.project.orino.planner.review.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.planner.review.dto.ReviewCompletionRequest;
import ds.project.orino.planner.review.dto.ReviewCompletionResponse;
import ds.project.orino.planner.review.dto.ReviewResponse;
import ds.project.orino.planner.review.sm2.Sm2Calculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

        if (current.getStatus() == ReviewStatus.COMPLETED) {
            throw new CustomException(ErrorCode.INVALID_STATE);
        }

        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        current.complete(request.rating(), today, now);

        Sm2Calculator.Result next = Sm2Calculator.next(
                current.getSequence(),
                current.getIntervalDays(),
                current.getEaseFactor(),
                request.rating()
        );

        ReviewSchedule nextReview = reviewScheduleRepository.save(new ReviewSchedule(
                memberId,
                current.getStudyUnitId(),
                current.getSequence() + 1,
                today.plusDays(next.intervalDays()),
                next.intervalDays(),
                next.easeFactor()
        ));

        return new ReviewCompletionResponse(
                ReviewResponse.from(current),
                ReviewResponse.from(nextReview)
        );
    }
}
