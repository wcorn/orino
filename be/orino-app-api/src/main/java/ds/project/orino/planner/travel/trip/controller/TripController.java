package ds.project.orino.planner.travel.trip.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.domain.planner.travel.entity.TripStatus;
import ds.project.orino.planner.travel.trip.dto.ShrinkPreviewResponse;
import ds.project.orino.planner.travel.trip.dto.TravelSummaryResponse;
import ds.project.orino.planner.travel.trip.dto.TripDetail;
import ds.project.orino.planner.travel.trip.dto.TripListResponse;
import ds.project.orino.planner.travel.trip.dto.TripWriteRequest;
import ds.project.orino.planner.travel.trip.service.TripService;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/travel")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    /** `/select` 카드와 여행 홈(S-01)이 함께 쓰는 요약. */
    @GetMapping("/summary")
    public ApiResponse<TravelSummaryResponse> summary(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(tripService.summary(memberId));
    }

    /** 여행 목록(S-02). {@code status} 생략 시 전체. 정렬은 서버가 확정한다. */
    @GetMapping("/trips")
    public ApiResponse<TripListResponse> list(@AuthenticationPrincipal Long memberId,
                                              @RequestParam(required = false) TripStatus status) {
        return ApiResponse.success(tripService.list(memberId, status));
    }

    /** 생성은 구간이 필수라 {@link TripWriteRequest.OnCreate} 그룹까지 함께 검증한다. */
    @PostMapping("/trips")
    public ApiResponse<TripDetail> create(
            @AuthenticationPrincipal Long memberId,
            @Validated({Default.class, TripWriteRequest.OnCreate.class})
            @RequestBody TripWriteRequest request) {
        return ApiResponse.success(tripService.create(memberId, request));
    }

    @GetMapping("/trips/{tripId}")
    public ApiResponse<TripDetail> detail(@AuthenticationPrincipal Long memberId,
                                          @PathVariable Long tripId) {
        return ApiResponse.success(tripService.detail(memberId, tripId));
    }

    /**
     * 전체 수정. 기간이 줄어 일정이 잘리는데 {@code confirmArchive}가 없으면 409로 거부하고
     * 이동 예정 건수를 함께 돌려준다.
     */
    @PutMapping("/trips/{tripId}")
    public ApiResponse<TripDetail> update(@AuthenticationPrincipal Long memberId,
                                          @PathVariable Long tripId,
                                          @Valid @RequestBody TripWriteRequest request) {
        return ApiResponse.success(tripService.update(memberId, tripId, request));
    }

    @DeleteMapping("/trips/{tripId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
                                    @PathVariable Long tripId) {
        tripService.delete(memberId, tripId);
        return ApiResponse.success();
    }

    /** 기간 단축 확인 모달용 — 이 기간으로 바꾸면 보관함으로 갈 일정 수. */
    @GetMapping("/trips/{tripId}/shrink-preview")
    public ApiResponse<ShrinkPreviewResponse> shrinkPreview(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long tripId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate) {
        return ApiResponse.success(
                tripService.shrinkPreview(memberId, tripId, startDate, endDate));
    }
}
