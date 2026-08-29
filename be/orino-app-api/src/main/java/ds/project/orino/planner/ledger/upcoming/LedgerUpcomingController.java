package ds.project.orino.planner.ledger.upcoming;

import ds.project.orino.common.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예정 목록과 캘린더.
 *
 * <p>회차 조작({@code PATCH /upcoming/occurrence})은 정기 항목 쪽에 있다 — 그건 <b>규칙에
 * 손대는 일</b>이라 예정을 읽는 것과 다른 관심사다.
 */
@RestController
@RequestMapping("/api/ledger")
public class LedgerUpcomingController {

    private final LedgerUpcomingService upcomingService;
    private final LedgerCalendarService calendarService;

    public LedgerUpcomingController(LedgerUpcomingService upcomingService,
                                    LedgerCalendarService calendarService) {
        this.upcomingService = upcomingService;
        this.calendarService = calendarService;
    }

    /** 4출처 UNION + 종류별 집계 + <b>최저 예상 잔액과 그 날짜·이유</b>. */
    @GetMapping("/upcoming")
    public ApiResponse<LedgerUpcomingDtos.UpcomingResponse> upcoming(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(upcomingService.upcoming(memberId, days));
    }

    /** 일별 수입·지출. <b>과거는 확정, 미래는 예정</b>을 따로 담아 화면이 연하게 그린다. */
    @GetMapping("/transactions/calendar")
    public ApiResponse<LedgerUpcomingDtos.CalendarResponse> calendar(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) String month) {
        return ApiResponse.success(calendarService.calendar(memberId, month));
    }
}
