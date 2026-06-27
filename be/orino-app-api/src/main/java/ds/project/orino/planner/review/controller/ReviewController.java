package ds.project.orino.planner.review.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.core.time.UserTimeZone;
import ds.project.orino.planner.review.dto.CalendarReviewsResponse;
import ds.project.orino.planner.review.dto.ReviewCompletionRequest;
import ds.project.orino.planner.review.dto.ReviewCompletionResponse;
import ds.project.orino.planner.review.dto.ReviewMirrorStatusResponse;
import ds.project.orino.planner.review.dto.ReviewMirrorToggleRequest;
import ds.project.orino.planner.review.dto.TodayReviewsResponse;
import ds.project.orino.planner.review.service.ReviewCompletionService;
import ds.project.orino.planner.review.service.ReviewMirrorService;
import ds.project.orino.planner.review.service.ReviewQueryService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/planner/reviews")
public class ReviewController {

    private final ReviewCompletionService reviewCompletionService;
    private final ReviewQueryService reviewQueryService;
    private final ReviewMirrorService reviewMirrorService;

    public ReviewController(ReviewCompletionService reviewCompletionService,
                            ReviewQueryService reviewQueryService,
                            ReviewMirrorService reviewMirrorService) {
        this.reviewCompletionService = reviewCompletionService;
        this.reviewQueryService = reviewQueryService;
        this.reviewMirrorService = reviewMirrorService;
    }

    @GetMapping("/today")
    public ApiResponse<TodayReviewsResponse> today(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(reviewQueryService.findToday(memberId));
    }

    @GetMapping("/calendar")
    public ApiResponse<CalendarReviewsResponse> calendar(
            @AuthenticationPrincipal Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(reviewQueryService.findCalendar(memberId, from, to));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<ReviewCompletionResponse> complete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody ReviewCompletionRequest request) {
        return ApiResponse.success(reviewCompletionService.complete(memberId, id, request));
    }

    /** 복습 → 보조 캘린더 미러 on/off. ON이면 보조 캘린더 보장 + 전 PENDING 백필, OFF면 미러 정리. 미연동 409. */
    @PutMapping("/mirror")
    public ApiResponse<ReviewMirrorStatusResponse> toggleMirror(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ReviewMirrorToggleRequest request) {
        ZoneId zone = UserTimeZone.get();
        ReviewMirrorStatusResponse result = request.enabled()
                ? reviewMirrorService.enableMirror(memberId, zone)
                : reviewMirrorService.disableMirror(memberId);
        return ApiResponse.success(result);
    }
}
