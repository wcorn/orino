package ds.project.orino.planner.review.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.review.entity.DifficultyFeedback;
import ds.project.orino.domain.review.entity.ReviewSchedule;
import ds.project.orino.domain.review.entity.ReviewStatus;
import ds.project.orino.domain.review.repository.ReviewScheduleRepository;
import ds.project.orino.planner.review.dto.SubmitFeedbackRequest;
import ds.project.orino.planner.review.dto.SubmitFeedbackResponse;
import ds.project.orino.planner.scheduling.dirty.DirtyScheduleMarker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewScheduleRepository reviewScheduleRepository;
    private final ReviewFeedbackProcessor reviewFeedbackProcessor;
    private final DirtyScheduleMarker dirtyScheduleMarker;

    public ReviewService(
            ReviewScheduleRepository reviewScheduleRepository,
            ReviewFeedbackProcessor reviewFeedbackProcessor,
            DirtyScheduleMarker dirtyScheduleMarker) {
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.reviewFeedbackProcessor = reviewFeedbackProcessor;
        this.dirtyScheduleMarker = dirtyScheduleMarker;
    }

    @Transactional
    public SubmitFeedbackResponse submitFeedback(
            Long memberId, Long reviewId, SubmitFeedbackRequest request) {
        ReviewSchedule review = loadOwnedReview(memberId, reviewId);
        if (review.getStatus() == ReviewStatus.COMPLETED
                || review.getStatus() == ReviewStatus.SKIPPED) {
            throw new CustomException(ErrorCode.INVALID_STATE);
        }

        DifficultyFeedback feedback = request.feedback();
        review.complete(feedback);
        reviewFeedbackProcessor.applyFeedback(review, feedback);

        dirtyScheduleMarker.markDirtyFromToday(memberId);

        ReviewSchedule next = findNextReview(review);
        return new SubmitFeedbackResponse(
                review.getId(),
                review.getStatus(),
                review.getDifficultyFeedback(),
                next != null ? next.getScheduledDate() : null,
                next != null ? next.getSequence() : null);
    }

    private ReviewSchedule findNextReview(ReviewSchedule current) {
        List<ReviewSchedule> upcoming = reviewScheduleRepository
                .findUpcomingByStudyUnit(
                        current.getStudyUnit().getId(),
                        current.getSequence(),
                        ReviewStatus.PENDING);
        return upcoming.stream()
                .min((a, b) -> a.getScheduledDate()
                        .compareTo(b.getScheduledDate()))
                .orElse(null);
    }

    private ReviewSchedule loadOwnedReview(Long memberId, Long reviewId) {
        ReviewSchedule review = reviewScheduleRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));
        Long ownerId = review.getStudyUnit().getMaterial()
                .getMember().getId();
        if (!ownerId.equals(memberId)) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return review;
    }
}
