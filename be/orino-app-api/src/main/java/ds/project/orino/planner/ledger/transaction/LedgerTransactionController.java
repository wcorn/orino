package ds.project.orino.planner.ledger.transaction;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.ledger.transaction.dto.BulkRequest;
import ds.project.orino.planner.ledger.transaction.dto.BulkResponse;
import ds.project.orino.planner.ledger.transaction.dto.RefundRequest;
import ds.project.orino.planner.ledger.transaction.dto.RefundResponse;
import ds.project.orino.planner.ledger.transaction.dto.SuggestionView;
import ds.project.orino.planner.ledger.transaction.dto.TransactionCreateRequest;
import ds.project.orino.planner.ledger.transaction.dto.TransactionCreatedResponse;
import ds.project.orino.planner.ledger.transaction.dto.TransactionListResponse;
import ds.project.orino.planner.ledger.transaction.dto.TransactionUpdateRequest;
import ds.project.orino.planner.ledger.transaction.dto.TransactionView;
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
import java.util.List;

/**
 * 거래 API. 전 구간 JWT 뒤에 있다 — 링크({@code s.orino.dev})와 달리 가계부에는
 * 비인증 공개 표면이 하나도 없다.
 */
@RestController
@RequestMapping("/api/ledger/transactions")
public class LedgerTransactionController {

    private final LedgerTransactionService transactionService;

    public LedgerTransactionController(LedgerTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * 내역. 확정과 예정이 같은 타임라인 위에 온다.
     *
     * <p>기본 구간은 <b>이번 달 1일 ~ 오늘+30일</b>이다 — 앞으로 나갈 돈이 보이지 않으면
     * 「월말에 얼마 남나」에 답할 수 없다.
     */
    @GetMapping
    public ApiResponse<TransactionListResponse> list(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(transactionService.list(memberId, from, to));
    }

    @GetMapping("/suggest")
    public ApiResponse<List<SuggestionView>> suggest(@AuthenticationPrincipal Long memberId,
                                                     @RequestParam(name = "q") String keyword) {
        return ApiResponse.success(transactionService.suggest(memberId, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<TransactionView> get(@AuthenticationPrincipal Long memberId,
                                            @PathVariable Long id) {
        return ApiResponse.success(transactionService.get(memberId, id));
    }

    /** 미래 날짜를 보내면 {@code savedAs=SCHEDULED}로 돌아온다 — 화면이 그 사실을 알린다. */
    @PostMapping
    public ApiResponse<TransactionCreatedResponse> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody TransactionCreateRequest request) {
        return ApiResponse.success(transactionService.create(memberId, request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<TransactionView> update(@AuthenticationPrincipal Long memberId,
                                               @PathVariable Long id,
                                               @Valid @RequestBody TransactionUpdateRequest request) {
        return ApiResponse.success(transactionService.update(memberId, id, request));
    }

    /** 소프트 삭제. 되돌릴 수 있어야 하고, 환불은 이 API가 아니라 {@code /refund}다. */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
                                    @PathVariable Long id) {
        transactionService.delete(memberId, id);
        return ApiResponse.success();
    }

    /** 환불·취소. <b>원 거래는 남는다</b> — 지우지 않고 반대 거래로 상쇄한다. */
    @PostMapping("/{id}/refund")
    public ApiResponse<RefundResponse> refund(@AuthenticationPrincipal Long memberId,
                                              @PathVariable Long id,
                                              @Valid @RequestBody RefundRequest request) {
        return ApiResponse.success(transactionService.refund(memberId, id, request));
    }

    /** 일괄 편집·삭제. 미분류 정리가 수십 건을 한 번에 넘긴다. */
    @PostMapping("/bulk")
    public ApiResponse<BulkResponse> bulk(@AuthenticationPrincipal Long memberId,
                                          @Valid @RequestBody BulkRequest request) {
        return ApiResponse.success(transactionService.bulk(memberId, request));
    }
}
