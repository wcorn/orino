package ds.project.orino.planner.google.routine;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.core.time.UserTimeZone;
import ds.project.orino.planner.google.routine.dto.RoutineCreateRequest;
import ds.project.orino.planner.google.routine.dto.RoutineListResponse;
import ds.project.orino.planner.google.routine.dto.RoutineSeriesSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 루틴(반복 이벤트) 시리즈 엔드포인트. 미연동 시 409(PLN-ERR-003).
 * 시간대는 {@code X-Timezone}({@link UserTimeZone}) 기준.
 */
@RestController
@RequestMapping("/api/planner/routines")
public class RoutineController {

    private final RoutineService routineService;
    private final RoutineQueryService routineQueryService;

    public RoutineController(RoutineService routineService,
                             RoutineQueryService routineQueryService) {
        this.routineService = routineService;
        this.routineQueryService = routineQueryService;
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
}
