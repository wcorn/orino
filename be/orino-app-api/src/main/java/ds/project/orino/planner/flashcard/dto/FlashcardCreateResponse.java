package ds.project.orino.planner.flashcard.dto;

import ds.project.orino.planner.review.dto.ReviewScheduleView;

public record FlashcardCreateResponse(
        FlashcardResponse flashcard,
        ReviewScheduleView firstReview
) {
}
