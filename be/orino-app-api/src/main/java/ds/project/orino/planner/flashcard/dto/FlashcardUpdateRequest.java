package ds.project.orino.planner.flashcard.dto;

import ds.project.orino.domain.planner.flashcard.entity.FlashcardType;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 카드 부분 수정 요청.
 *
 * <p>{@code type}이 있으면 해당 종류로 전환하며, 전환 후 상태가 대상 종류 제약을 만족해야 한다.
 * 종류별 제약 위반 시 {@code SP-ERR-002}(400)로 응답한다.
 */
public record FlashcardUpdateRequest(
        FlashcardType type,

        @Size(min = 1, max = 1000, message = "front는 1~1000자여야 합니다.")
        String front,

        String back,

        List<OrderingItem> items
) {
}
