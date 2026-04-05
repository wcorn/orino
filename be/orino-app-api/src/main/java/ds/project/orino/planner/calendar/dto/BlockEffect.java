package ds.project.orino.planner.calendar.dto;

import java.time.LocalDate;

/**
 * 블록 완료 시 발생하는 후속 처리 결과.
 * type에 따라 채워지는 필드가 달라진다.
 *
 * <ul>
 *   <li>REVIEW_CREATED: STUDY 완료 → 복습 일정 자동 생성 (nextReviewDate, message)</li>
 *   <li>STREAK_UPDATED: ROUTINE 완료 → 루틴 체크 기록</li>
 *   <li>FEEDBACK_REQUIRED: REVIEW 완료 → 난이도 피드백 필요 (reviewId)</li>
 *   <li>TODO_COMPLETED: TODO 완료</li>
 *   <li>FIXED_COMPLETED: FIXED 일정 완료</li>
 * </ul>
 */
public record BlockEffect(
        String type,
        String message,
        LocalDate nextReviewDate,
        Long reviewId,
        Boolean feedbackRequired) {

    public static BlockEffect reviewCreated(LocalDate nextReviewDate, int count) {
        String message = String.format(
                "복습 %d회가 자동 생성되었습니다.", count);
        return new BlockEffect("REVIEW_CREATED", message,
                nextReviewDate, null, null);
    }

    public static BlockEffect streakUpdated() {
        return new BlockEffect("STREAK_UPDATED",
                "루틴 체크가 기록되었습니다.", null, null, null);
    }

    public static BlockEffect feedbackRequired(Long reviewId) {
        return new BlockEffect("FEEDBACK_REQUIRED",
                "난이도 피드백이 필요합니다.", null, reviewId, true);
    }

    public static BlockEffect todoCompleted() {
        return new BlockEffect("TODO_COMPLETED",
                "할 일이 완료되었습니다.", null, null, null);
    }

    public static BlockEffect fixedCompleted() {
        return new BlockEffect("FIXED_COMPLETED",
                "고정 일정이 완료되었습니다.", null, null, null);
    }
}
