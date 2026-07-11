package ds.project.orino.planner.review.dto;

import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.entity.FlashcardType;

/**
 * 복습 허브 목록(앞으로/완료) 항목에 동봉하는 카드 뷰. 목록은 앞면만 보여주므로 back/items는 싣지 않는다.
 * siblingGroupId는 null도 명시적으로 노출한다(양방향 파생 판단용).
 */
public record ReviewCardView(
        Long id,
        FlashcardType type,
        String front,
        Long siblingGroupId,
        ReviewCardMaterial material
) {
    public static ReviewCardView of(Flashcard f, ReviewCardMaterial material) {
        return new ReviewCardView(f.getId(), f.getType(), f.getFront(), f.getSiblingGroupId(), material);
    }
}
