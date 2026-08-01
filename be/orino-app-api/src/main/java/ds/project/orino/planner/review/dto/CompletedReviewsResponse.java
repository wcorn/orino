package ds.project.orino.planner.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 완료된 복습 목록 페이지.
 * {@code totalCount}는 현재 필터에 걸리는 전체 건수로, 첫 페이지(cursor 없음)에서만 채운다.
 * 이후 페이지는 null이라 무한스크롤마다 COUNT를 돌지 않는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompletedReviewsResponse(
        List<CompletedReviewItem> items,
        String nextCursor,
        boolean hasNext,
        Long totalCount
) {
}
