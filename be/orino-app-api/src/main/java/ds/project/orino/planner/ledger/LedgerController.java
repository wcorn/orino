package ds.project.orino.planner.ledger;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.ledger.fx.LedgerFxRateResponse;
import ds.project.orino.planner.ledger.fx.LedgerFxService;
import ds.project.orino.planner.ledger.settings.LedgerSettingsService;
import ds.project.orino.planner.ledger.stats.LedgerStatsResponse;
import ds.project.orino.planner.ledger.stats.LedgerStatsService;
import ds.project.orino.planner.ledger.settings.dto.SettingsDtos;
import ds.project.orino.planner.ledger.summary.LedgerDashboardResponse;
import ds.project.orino.planner.ledger.summary.LedgerSummaryResponse;
import ds.project.orino.planner.ledger.summary.LedgerSummaryService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;

/** 모듈 단위 API — 요약 · 설정 · 환율 조회. 자산·카테고리·거래는 각자의 컨트롤러가 맡는다. */
@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerSummaryService summaryService;
    private final LedgerSettingsService settingsService;
    private final LedgerFxService fxService;
    private final LedgerStatsService statsService;

    public LedgerController(LedgerSummaryService summaryService,
                            LedgerSettingsService settingsService,
                            LedgerFxService fxService,
                            LedgerStatsService statsService) {
        this.summaryService = summaryService;
        this.settingsService = settingsService;
        this.fxService = fxService;
        this.statsService = statsService;
    }

    /** {@code /select} 가계부 카드와 대시보드가 함께 쓴다. v1.5 값들은 아직 {@code null}이다. */
    @GetMapping("/summary")
    public ApiResponse<LedgerSummaryResponse> summary(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(summaryService.summary(memberId));
    }

    /**
     * 대시보드. <b>v1은 셋만 내린다</b> — 이미 쓴 돈 · 이번 달 수입 · 정리할 내역.
     * 2축 요약·미납·다가오는 결제는 필드 자체가 없다(D-7).
     */
    @GetMapping("/dashboard")
    public ApiResponse<LedgerDashboardResponse> dashboard(
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(summaryService.dashboard(memberId));
    }

    /** 카테고리 통계. {@code period}가 없으면 지금 속한 구간이다. */
    @GetMapping("/stats")
    public ApiResponse<LedgerStatsResponse> stats(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) String period) {
        return ApiResponse.success(
                statsService.stats(memberId, period == null ? null : YearMonth.parse(period)));
    }

    @GetMapping("/settings")
    public ApiResponse<SettingsDtos.View> settings(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(settingsService.get(memberId));
    }

    @PatchMapping("/settings")
    public ApiResponse<SettingsDtos.View> updateSettings(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody SettingsDtos.Update request) {
        return ApiResponse.success(settingsService.update(memberId, request));
    }

    /**
     * 환율 조회. <b>못 가져와도 에러가 아니다</b> — {@code rate}가 {@code null}로 온다.
     * 화면은 그때 직접 입력 칸을 연다. 여기서 500을 내면 외화 입력 자체가 막힌다.
     */
    @GetMapping("/fx/rate")
    public ApiResponse<LedgerFxRateResponse> fxRate(
            @RequestParam String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        return ApiResponse.success(fxService.lookup(currency, on));
    }
}
