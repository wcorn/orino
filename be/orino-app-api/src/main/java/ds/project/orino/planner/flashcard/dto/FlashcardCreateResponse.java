package ds.project.orino.planner.flashcard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import ds.project.orino.planner.review.dto.ReviewScheduleView;

/**
 * 카드 생성 응답. 양방향으로 생성하면 짝 카드를 {@code sibling}에 함께 담는다(단방향이면 생략).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FlashcardCreateResponse(
        FlashcardResponse flashcard,
        ReviewScheduleView firstReview,
        FlashcardCreateResponse sibling
) {
    public static FlashcardCreateResponse of(FlashcardResponse flashcard, ReviewScheduleView firstReview) {
        return new FlashcardCreateResponse(flashcard, firstReview, null);
    }
}
