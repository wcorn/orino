package ds.project.orino.planner.travel.activity.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.travel.activity.dto.ActivityLogRequest;
import ds.project.orino.planner.travel.activity.dto.ActivityLogResponse;
import ds.project.orino.planner.travel.activity.dto.ActivityResponse;
import ds.project.orino.planner.travel.activity.dto.ActivityWriteRequest;
import ds.project.orino.planner.travel.activity.dto.ReorderRequest;
import ds.project.orino.planner.travel.activity.dto.ReorderResponse;
import ds.project.orino.planner.travel.activity.service.ActivityService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/travel")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    /** 일정 추가. {@code sortOrder}는 서버가 해당 날짜 끝에 배정한다. */
    @PostMapping("/trips/{tripId}/activities")
    public ApiResponse<ActivityResponse> create(@AuthenticationPrincipal Long memberId,
                                                @PathVariable Long tripId,
                                                @Valid @RequestBody ActivityWriteRequest request) {
        return ApiResponse.success(activityService.create(memberId, tripId, request));
    }

    @GetMapping("/activities/{activityId}")
    public ApiResponse<ActivityResponse> detail(@AuthenticationPrincipal Long memberId,
                                                @PathVariable Long activityId) {
        return ApiResponse.success(activityService.detail(memberId, activityId));
    }

    /** 계획 영역 전체 수정. */
    @PutMapping("/activities/{activityId}")
    public ApiResponse<ActivityResponse> update(@AuthenticationPrincipal Long memberId,
                                                @PathVariable Long activityId,
                                                @Valid @RequestBody ActivityWriteRequest request) {
        return ApiResponse.success(activityService.update(memberId, activityId, request));
    }

    @DeleteMapping("/activities/{activityId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
                                    @PathVariable Long activityId) {
        activityService.delete(memberId, activityId);
        return ApiResponse.success();
    }

    /**
     * 기록(평점·메모) 저장. 사진과 분리된 요청이라 사진 업로드가 실패해도 이건 남는다.
     *
     * <p>둘 다 비우면 기록을 지우고 {@code data: null}을 돌려준다.
     */
    @PutMapping("/activities/{activityId}/log")
    public ApiResponse<ActivityLogResponse> saveLog(@AuthenticationPrincipal Long memberId,
                                                    @PathVariable Long activityId,
                                                    @Valid @RequestBody ActivityLogRequest request) {
        return ApiResponse.success(activityService.saveLog(memberId, activityId, request));
    }

    /** 드래그 결과 반영 — 순서 변경과 날짜 이동을 한 트랜잭션으로 처리한다. */
    @PutMapping("/trips/{tripId}/activities/order")
    public ApiResponse<ReorderResponse> reorder(@AuthenticationPrincipal Long memberId,
                                                @PathVariable Long tripId,
                                                @Valid @RequestBody ReorderRequest request) {
        return ApiResponse.success(
                new ReorderResponse(activityService.reorder(memberId, tripId, request)));
    }
}
