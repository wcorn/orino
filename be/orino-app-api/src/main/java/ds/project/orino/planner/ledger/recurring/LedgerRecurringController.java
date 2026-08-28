package ds.project.orino.planner.ledger.recurring;

import ds.project.orino.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정기 항목 · 회차 API(API 스펙 §4·§6).
 *
 * <p><b>「이 건만 / 이후 모두 / 전체」를 묻는 엔드포인트가 없다.</b> 규칙 수정은 앞으로만
 * 바꾸고(PATCH {@code /recurring/{id}}), 이번 회차만 다르게 하려면 그 회차를 손댄다
 * (PATCH {@code /upcoming/occurrence}) — 물어보는 대신 두 경로로 갈라 두었다(확정 명세 §6.5).
 */
@RestController
@RequestMapping("/api/ledger")
public class LedgerRecurringController {

    private final LedgerRecurringService recurringService;
    private final LedgerOccurrenceService occurrenceService;

    public LedgerRecurringController(LedgerRecurringService recurringService,
                                     LedgerOccurrenceService occurrenceService) {
        this.recurringService = recurringService;
        this.occurrenceService = occurrenceService;
    }

    /** 목록 + 스탯 + 점검 신호 4종. 해지한 항목도 「종료됨」으로 함께 온다. */
    @GetMapping("/recurring")
    public ApiResponse<LedgerRecurringDtos.RecurringListResponse> list(
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success(recurringService.list(memberId));
    }

    @PostMapping("/recurring")
    public ApiResponse<LedgerRecurringDtos.RecurringView> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody LedgerRecurringDtos.CreateRequest request) {
        return ApiResponse.success(recurringService.create(memberId, request));
    }

    /** 수정 — <b>이후 예정에 즉시 반영되고 과거 내역은 불변</b>이다. */
    @PatchMapping("/recurring/{id}")
    public ApiResponse<LedgerRecurringDtos.RecurringView> update(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody LedgerRecurringDtos.UpdateRequest request) {
        return ApiResponse.success(recurringService.update(memberId, id, request));
    }

    @PostMapping("/recurring/{id}/pause")
    public ApiResponse<LedgerRecurringDtos.RecurringView> pause(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody LedgerRecurringDtos.PauseRequest request) {
        return ApiResponse.success(recurringService.pause(memberId, id, request));
    }

    @PostMapping("/recurring/{id}/resume")
    public ApiResponse<LedgerRecurringDtos.RecurringView> resume(
            @AuthenticationPrincipal Long memberId, @PathVariable Long id) {
        return ApiResponse.success(recurringService.resume(memberId, id));
    }

    /**
     * 해지. {@code revertPostedAfter}에 기본값이 없다 — 소급 해지는 이미 원장에 들어간 것을
     * 되돌리는 유일한 경로라 사람이 매번 답해야 한다.
     */
    @PostMapping("/recurring/{id}/end")
    public ApiResponse<LedgerRecurringDtos.EndResponse> end(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody LedgerRecurringDtos.EndRequest request) {
        return ApiResponse.success(recurringService.end(memberId, id, request));
    }

    @GetMapping("/recurring/{id}/history")
    public ApiResponse<LedgerRecurringDtos.HistoryResponse> history(
            @AuthenticationPrincipal Long memberId, @PathVariable Long id) {
        return ApiResponse.success(recurringService.history(memberId, id));
    }

    /** 회차 조작 — 금액·건너뛰기·이동·미납·되돌리기. 손댄 회차만 1행이 남는다. */
    @PatchMapping("/upcoming/occurrence")
    public ApiResponse<LedgerRecurringDtos.OccurrenceView> occurrence(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody LedgerRecurringDtos.OccurrenceRequest request) {
        return ApiResponse.success(occurrenceService.apply(memberId, request));
    }

    /** 미납 건을 <b>실제 출금일로</b> 확정한다. */
    @PostMapping("/upcoming/occurrence/confirm")
    public ApiResponse<LedgerRecurringDtos.OccurrenceView> confirmOccurrence(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody LedgerRecurringDtos.ConfirmRequest request) {
        return ApiResponse.success(occurrenceService.confirm(memberId, request));
    }
}
