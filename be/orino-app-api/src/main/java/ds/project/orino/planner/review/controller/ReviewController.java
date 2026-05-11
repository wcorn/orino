package ds.project.orino.planner.review.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.review.dto.ReviewCompletionRequest;
import ds.project.orino.planner.review.dto.ReviewCompletionResponse;
import ds.project.orino.planner.review.dto.TodayReviewsResponse;
import ds.project.orino.planner.review.dto.UnitCompletionResponse;
import ds.project.orino.planner.review.service.ReviewCompletionService;
import ds.project.orino.planner.review.service.ReviewQueryService;
import ds.project.orino.planner.review.service.UnitCompletionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planner")
public class ReviewController {

    private final UnitCompletionService unitCompletionService;
    private final ReviewCompletionService reviewCompletionService;
    private final ReviewQueryService reviewQueryService;

    public ReviewController(UnitCompletionService unitCompletionService,
                            ReviewCompletionService reviewCompletionService,
                            ReviewQueryService reviewQueryService) {
        this.unitCompletionService = unitCompletionService;
        this.reviewCompletionService = reviewCompletionService;
        this.reviewQueryService = reviewQueryService;
    }

    @GetMapping("/reviews/today")
    public ApiResponse<TodayReviewsResponse> todayReviews(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(reviewQueryService.getTodayReviews(memberId));
    }

    @PostMapping("/units/{id}/complete")
    public ApiResponse<UnitCompletionResponse> completeUnit(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        return ApiResponse.success(unitCompletionService.complete(memberId, id));
    }

    @PostMapping("/reviews/{id}/complete")
    public ApiResponse<ReviewCompletionResponse> completeReview(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody ReviewCompletionRequest request) {
        return ApiResponse.success(reviewCompletionService.complete(memberId, id, request));
    }
}
