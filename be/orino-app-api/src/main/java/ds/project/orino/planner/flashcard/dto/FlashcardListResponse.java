package ds.project.orino.planner.flashcard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 카드 목록 한 페이지. {@code totalCount}는 <b>필터를 적용한</b> 총 개수(페이지 길이 아님)이고,
 * {@code nextCursor}는 마지막 페이지면 생략된다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FlashcardListResponse(
        List<FlashcardResponse> flashcards,
        long totalCount,
        String nextCursor,
        boolean hasNext
) {
}
