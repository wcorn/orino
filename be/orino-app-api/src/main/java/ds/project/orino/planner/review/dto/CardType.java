package ds.project.orino.planner.review.dto;

import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.entity.FlashcardType;

/**
 * 복습 허브 목록에서 노출하는 카드 종류. {@code PAIR}는 저장 타입이 아니라
 * BASIC + siblingGroupId 조합에서 파생한다.
 */
public enum CardType {
    BASIC,
    ORDERING,
    PAIR;

    public static CardType from(Flashcard f) {
        if (f.getType() == FlashcardType.ORDERING) {
            return ORDERING;
        }
        return f.getSiblingGroupId() != null ? PAIR : BASIC;
    }
}
