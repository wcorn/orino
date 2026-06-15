package ds.project.orino.planner.google.calendar;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.core.time.UserTimeZone;
import ds.project.orino.planner.google.calendar.dto.PlannerCalendarFeed;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 통합 캘린더 피드 엔드포인트. 일정+할일+복습을 한 번의 호출로 반환한다.
 * 시간대는 {@code X-Timezone}({@link UserTimeZone}) 기준.
 */
@RestController
@RequestMapping("/api/planner/calendar")
public class PlannerCalendarController {

    private final PlannerCalendarService plannerCalendarService;

    public PlannerCalendarController(PlannerCalendarService plannerCalendarService) {
        this.plannerCalendarService = plannerCalendarService;
    }

    @GetMapping
    public ApiResponse<PlannerCalendarFeed> feed(
            @AuthenticationPrincipal Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String view) {
        // view(month|week|day)는 표시 힌트이며 서버 응답에는 영향을 주지 않는다.
        return ApiResponse.success(
                plannerCalendarService.getFeed(memberId, from, to, UserTimeZone.get()));
    }
}
