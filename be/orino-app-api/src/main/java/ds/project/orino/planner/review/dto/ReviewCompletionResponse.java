package ds.project.orino.planner.review.dto;

import java.util.List;

/**
 * 복습 완료 응답. {@code buriedReviewIds}는 sibling burying으로 오늘 큐에서 밀려난 짝 복습 id들
 * (없으면 빈 배열). FE는 이 id들을 세션 복습 큐에서 제거한다.
 */
public record ReviewCompletionResponse(
        CompletedReviewView completed,
        ReviewScheduleView nextReview,
        List<Long> buriedReviewIds
) {
}
