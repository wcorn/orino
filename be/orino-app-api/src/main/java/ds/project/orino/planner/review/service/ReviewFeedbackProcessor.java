package ds.project.orino.planner.review.service;

import ds.project.orino.domain.review.entity.DifficultyFeedback;
import ds.project.orino.domain.review.entity.ReviewSchedule;
import ds.project.orino.domain.review.entity.ReviewStatus;
import ds.project.orino.domain.review.repository.ReviewScheduleRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 복습 난이도 피드백에 따라 남은 복습 간격을 조절한다.
 *
 * <ul>
 *     <li>EASY: 남은 간격 × 1.5 (올림)</li>
 *     <li>NORMAL: 변경 없음</li>
 *     <li>HARD: 남은 간격 × 0.7 (올림) + 추가 복습 1회 삽입</li>
 * </ul>
 *
 * 간격 스케일은 완료된 복습의 scheduledDate 를 기준으로 재계산하여
 * 각 PENDING 복습의 scheduledDate 를 업데이트한다.
 */
@Component
public class ReviewFeedbackProcessor {

    private static final BigDecimal EASY_FACTOR = new BigDecimal("1.5");
    private static final BigDecimal HARD_FACTOR = new BigDecimal("0.7");
    private static final int HARD_EXTRA_INTERVAL_DAYS = 2;

    private final ReviewScheduleRepository reviewScheduleRepository;

    public ReviewFeedbackProcessor(
            ReviewScheduleRepository reviewScheduleRepository) {
        this.reviewScheduleRepository = reviewScheduleRepository;
    }

    public void applyFeedback(ReviewSchedule completed,
                              DifficultyFeedback feedback) {
        if (feedback == null || feedback == DifficultyFeedback.NORMAL) {
            return;
        }
        LocalDate baseDate = completed.getScheduledDate();
        List<ReviewSchedule> upcoming = reviewScheduleRepository
                .findUpcomingByStudyUnit(
                        completed.getStudyUnit().getId(),
                        completed.getSequence(),
                        ReviewStatus.PENDING);
        switch (feedback) {
            case EASY -> scaleIntervals(upcoming, baseDate, EASY_FACTOR);
            case HARD -> {
                scaleIntervals(upcoming, baseDate, HARD_FACTOR);
                insertExtraReview(completed, baseDate);
            }
            default -> {
            }
        }
    }

    private void scaleIntervals(List<ReviewSchedule> reviews,
                                LocalDate baseDate, BigDecimal factor) {
        for (ReviewSchedule r : reviews) {
            long originalGap = ChronoUnit.DAYS.between(
                    baseDate, r.getScheduledDate());
            if (originalGap <= 0) {
                continue;
            }
            long scaledGap = BigDecimal.valueOf(originalGap)
                    .multiply(factor)
                    .setScale(0, RoundingMode.CEILING)
                    .longValueExact();
            if (scaledGap < 1) {
                scaledGap = 1;
            }
            r.reschedule(baseDate.plusDays(scaledGap));
        }
    }

    private void insertExtraReview(ReviewSchedule completed,
                                   LocalDate baseDate) {
        int maxSequence = reviewScheduleRepository
                .findMaxSequenceByStudyUnit(
                        completed.getStudyUnit().getId());
        ReviewSchedule extra = new ReviewSchedule(
                completed.getStudyUnit(),
                maxSequence + 1,
                baseDate.plusDays(HARD_EXTRA_INTERVAL_DAYS));
        reviewScheduleRepository.save(extra);
    }
}
