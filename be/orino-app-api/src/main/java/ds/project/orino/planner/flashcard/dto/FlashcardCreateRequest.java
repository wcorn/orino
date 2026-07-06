package ds.project.orino.planner.flashcard.dto;

import ds.project.orino.domain.planner.flashcard.entity.FlashcardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 카드 생성 요청.
 *
 * <p>{@code type} 생략 시 BASIC. 종류별 제약(BASIC: back 필수 / ORDERING: items 3~7개)은
 * 서비스에서 검증하며, 위반 시 {@code SP-ERR-002}(400)로 응답한다.
 */
public record FlashcardCreateRequest(
        FlashcardType type,

        @NotBlank(message = "front는 비어 있을 수 없습니다.")
        @Size(min = 1, max = 1000, message = "front는 1~1000자여야 합니다.")
        String front,

        String back,

        List<OrderingItem> items,

        /** 켜면 역방향 카드도 함께 생성한다. BASIC + back 존재일 때만 허용(위반 시 SP-ERR-002). */
        Boolean bidirectional
) {
    public FlashcardType typeOrDefault() {
        return type == null ? FlashcardType.BASIC : type;
    }

    public boolean isBidirectional() {
        return Boolean.TRUE.equals(bidirectional);
    }
}
