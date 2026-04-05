package ds.project.orino.planner.review.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.review.dto.SubmitFeedbackRequest;
import ds.project.orino.planner.review.dto.SubmitFeedbackResponse;
import ds.project.orino.planner.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/{id}/feedback")
    public ResponseEntity<ApiResponse<SubmitFeedbackResponse>> submitFeedback(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody SubmitFeedbackRequest request) {
        Long memberId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.submitFeedback(memberId, id, request)));
    }
}
