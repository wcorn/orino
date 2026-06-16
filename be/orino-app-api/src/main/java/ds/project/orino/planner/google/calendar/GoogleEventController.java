package ds.project.orino.planner.google.calendar;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.core.time.UserTimeZone;
import ds.project.orino.planner.google.calendar.dto.EventRequest;
import ds.project.orino.planner.google.calendar.dto.PlannerEvent;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Google Calendar 일정 양방향 쓰기 프록시. 미연동 시 409(PLN-ERR-003).
 * 시간대는 {@code X-Timezone}({@link UserTimeZone}) 기준.
 */
@RestController
@RequestMapping("/api/planner/calendar/events")
public class GoogleEventController {

    private final GoogleEventCommandService googleEventCommandService;

    public GoogleEventController(GoogleEventCommandService googleEventCommandService) {
        this.googleEventCommandService = googleEventCommandService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PlannerEvent>> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody EventRequest request) {
        PlannerEvent created = googleEventCommandService.create(memberId, request, UserTimeZone.get());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PatchMapping("/{eventId}")
    public ApiResponse<PlannerEvent> update(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String eventId,
            @Valid @RequestBody EventRequest request) {
        return ApiResponse.success(
                googleEventCommandService.update(memberId, eventId, request, UserTimeZone.get()));
    }

    @DeleteMapping("/{eventId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String eventId) {
        googleEventCommandService.delete(memberId, eventId);
        return ApiResponse.success();
    }
}
