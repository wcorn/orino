package ds.project.orino.planner.google.routine;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.core.time.UserTimeZone;
import ds.project.orino.planner.google.routine.dto.RoutineCheckRequest;
import ds.project.orino.planner.google.routine.dto.RoutineCheckResponse;
import ds.project.orino.planner.google.routine.dto.RoutineCreateRequest;
import ds.project.orino.planner.google.routine.dto.RoutineEditRequest;
import ds.project.orino.planner.google.routine.dto.RoutineListResponse;
import ds.project.orino.planner.google.routine.dto.RoutineSeriesSummary;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 루틴(반복 이벤트) 시리즈 엔드포인트. 미연동 시 409(PLN-ERR-003).
 * 시간대는 {@code X-Timezone}({@link UserTimeZone}) 기준.
 */
@RestController
@RequestMapping("/api/planner/routines")
public class RoutineController {

    private final RoutineService routineService;
    private final RoutineQueryService routineQueryService;
    private final RoutineCheckService routineCheckService;
    private final RoutineSeriesService routineSeriesService;

    public RoutineController(RoutineService routineService,
                             RoutineQueryService routineQueryService,
                             RoutineCheckService routineCheckService,
                             RoutineSeriesService routineSeriesService) {
        this.routineService = routineService;
        this.routineQueryService = routineQueryService;
        this.routineCheckService = routineCheckService;
        this.routineSeriesService = routineSeriesService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoutineSeriesSummary>> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody RoutineCreateRequest request) {
        RoutineSeriesSummary created = routineService.create(memberId, request, UserTimeZone.get());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping
    public ApiResponse<RoutineListResponse> list(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(
                new RoutineListResponse(routineQueryService.list(memberId, UserTimeZone.get())));
    }

    /** 습관 완료 체크 토글. schedule 루틴에 호출하면 400(체크 비대상). */
    @PostMapping("/{recurringEventId}/check")
    public ApiResponse<RoutineCheckResponse> check(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String recurringEventId,
            @Valid @RequestBody RoutineCheckRequest request) {
        return ApiResponse.success(
                routineCheckService.toggle(memberId, recurringEventId, request.date(), request.done()));
    }

    /** 시리즈/인스턴스 편집. scope=all|following|instance, following/instance는 instanceDate 필요. */
    @PatchMapping("/{eventId}")
    public ApiResponse<RoutineSeriesSummary> edit(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String eventId,
            @RequestParam String scope,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate instanceDate,
            @Valid @RequestBody RoutineEditRequest request) {
        return ApiResponse.success(routineSeriesService.edit(
                memberId, eventId, RoutineScope.from(scope), instanceDate, request, UserTimeZone.get()));
    }

    /** 시리즈/인스턴스 삭제. scope=all|following|instance, following/instance는 instanceDate 필요. */
    @DeleteMapping("/{eventId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String eventId,
            @RequestParam String scope,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate instanceDate) {
        routineSeriesService.delete(
                memberId, eventId, RoutineScope.from(scope), instanceDate, UserTimeZone.get());
        return ApiResponse.success();
    }
}
