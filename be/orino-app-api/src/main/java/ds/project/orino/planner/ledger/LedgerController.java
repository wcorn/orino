package ds.project.orino.planner.ledger;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.domain.planner.ledger.entity.LedgerPerspective;
import ds.project.orino.planner.ledger.fx.LedgerFxRateResponse;
import ds.project.orino.planner.ledger.fx.LedgerFxService;
import ds.project.orino.planner.ledger.receipt.LedgerReceiptDtos;
import ds.project.orino.planner.ledger.receipt.LedgerReceiptService;
import ds.project.orino.planner.ledger.receipt.LedgerReceiptStorageService;
import ds.project.orino.planner.ledger.settings.LedgerSettingsService;
import ds.project.orino.planner.ledger.settings.dto.SettingsDtos;
import ds.project.orino.planner.ledger.stats.LedgerSearchDtos;
import ds.project.orino.planner.ledger.stats.LedgerSearchService;
import ds.project.orino.planner.ledger.stats.LedgerStatsResponse;
import ds.project.orino.planner.ledger.stats.LedgerStatsService;
import ds.project.orino.planner.ledger.summary.LedgerDashboardResponse;
import ds.project.orino.planner.ledger.summary.LedgerSummaryResponse;
import ds.project.orino.planner.ledger.summary.LedgerSummaryService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.YearMonth;

/** 모듈 단위 API — 요약 · 설정 · 환율 조회. 자산·카테고리·거래는 각자의 컨트롤러가 맡는다. */
@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerSummaryService summaryService;
    private final LedgerSettingsService settingsService;
    private final LedgerFxService fxService;
    private final LedgerStatsService statsService;
    private final LedgerSearchService searchService;
    private final LedgerReceiptStorageService receiptStorageService;
    private final LedgerReceiptService receiptService;

    public LedgerController(LedgerSummaryService summaryService,
                            LedgerSettingsService settingsService,
                            LedgerFxService fxService,
                            LedgerStatsService statsService,
                            LedgerSearchService searchService,
                            LedgerReceiptStorageService receiptStorageService,
                            LedgerReceiptService receiptService) {
        this.summaryService = summaryService;
        this.settingsService = settingsService;
        this.fxService = fxService;
        this.statsService = statsService;
        this.searchService = searchService;
        this.receiptStorageService = receiptStorageService;
        this.receiptService = receiptService;
    }

    /** {@code /select} 가계부 카드와 대시보드가 함께 쓴다. */
    @GetMapping("/summary")
    public ApiResponse<LedgerSummaryResponse> summary(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(summaryService.summary(memberId));
    }

    /** 대시보드. <b>2축 요약</b>이 중심이다 — 소비와 현금 유출은 다른 질문에 답한다(§8.2). */
    @GetMapping("/dashboard")
    public ApiResponse<LedgerDashboardResponse> dashboard(
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(summaryService.dashboard(memberId));
    }

    /**
     * 통계. {@code period}가 없으면 지금 속한 구간, {@code perspective}가 없으면 설정의 기본값이다.
     *
     * <p><b>청구서·예정·예상 잔액 API는 이 파라미터를 받지 않는다</b> — 그쪽은 언제나 청구
     * 기준이고, 「9월 14일에 얼마 빠지나」에 소비 관점이 낄 자리가 없다(§10.1).
     *
     * <p>{@code excludeTrip}은 여행에 붙은 지출을 뺀다(§11.2). <b>기본은 끔</b>이다 —
     * 통계는 평상시 지출과 섞어 집계하는 것이 기본이고, 여행은 걷어 낼 수 있는 렌즈다.
     */
    @GetMapping("/stats")
    public ApiResponse<LedgerStatsResponse> stats(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) LedgerPerspective perspective,
            @RequestParam(defaultValue = "false") boolean excludeTrip) {
        return ApiResponse.success(statsService.stats(
                memberId, period == null ? null : YearMonth.parse(period), perspective,
                excludeTrip));
    }

    /**
     * 복합 검색(§10.2). 결과를 그대로 일괄 편집({@code POST /transactions/bulk})에 넘긴다.
     *
     * <p>일괄 편집 엔드포인트를 여기 또 만들지 않는다 — 같은 일을 하는 문이 둘이 되면
     * 한쪽만 고쳐지는 날이 온다.
     */
    @PostMapping("/stats/search")
    public ApiResponse<LedgerSearchDtos.SearchResponse> search(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody LedgerSearchDtos.SearchRequest request) {
        return ApiResponse.success(searchService.search(memberId, request));
    }

    /**
     * 영수증 업로드용 presigned URL. 바이트는 BE를 거치지 않고 브라우저가 MinIO에 직접 PUT 한다 —
     * 일상기록과 <b>같은 버킷</b>을 쓰고 prefix만 다르다.
     */
    @PostMapping("/receipts/upload-url")
    public ApiResponse<LedgerReceiptDtos.UploadUrl> receiptUploadUrl(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody LedgerReceiptDtos.UploadUrlRequest request) {
        return ApiResponse.success(
                receiptStorageService.createUploadUrl(memberId, request.contentType()));
    }

    /** 첨부를 뗀다. <b>오브젝트는 지우지 않는다</b> — 되돌릴 수 있어야 한다. */
    @DeleteMapping("/receipts/{id}")
    public ApiResponse<Void> detachReceipt(@AuthenticationPrincipal Long memberId,
                                           @PathVariable Long id) {
        receiptService.detach(memberId, id);
        return ApiResponse.success();
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
