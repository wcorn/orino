package ds.project.orino.planner.ledger.recurring;

import ds.project.orino.domain.planner.ledger.entity.LedgerAmountType;
import ds.project.orino.domain.planner.ledger.entity.LedgerBusinessDayPolicy;
import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.entity.LedgerFrequencyType;
import ds.project.orino.domain.planner.ledger.entity.LedgerOverrideAction;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurringKind;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurringStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** 정기 항목 API의 오가는 값들(API 스펙 §6). */
public final class LedgerRecurringDtos {

    private LedgerRecurringDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull LedgerRecurringKind kind,
            @NotNull LedgerFlow txType,
            @NotNull @Positive Long amount,
            LedgerAmountType amountType,
            @NotNull Long assetId,
            Long counterAssetId,
            Long categoryId,
            @NotNull LedgerFrequencyType freqType,
            Integer freqInterval,
            Integer freqDay,
            Integer freqMonth,
            LedgerBusinessDayPolicy businessDayPolicy,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @Size(max = 500) String cancelUrl,
            @Size(max = 500) String memo) {
    }

    /**
     * 부분 수정. 보낸 값만 바뀐다.
     *
     * <p>규칙을 고치면 <b>앞으로의 예정이 즉시 새 규칙대로</b> 바뀐다 — 회차를 파생 계산하므로
     * 저절로 그렇게 된다. "이 건만 / 이후 모두 / 전체"를 묻지 않고, 이미 원장에 들어간 과거
     * 내역은 건드리지 않는다(확정 명세 §6.5).
     */
    public record UpdateRequest(
            @Size(max = 80) String name,
            LedgerRecurringKind kind,
            @Positive Long amount,
            LedgerAmountType amountType,
            Long assetId,
            Long counterAssetId,
            Long categoryId,
            LedgerFrequencyType freqType,
            Integer freqInterval,
            Integer freqDay,
            Integer freqMonth,
            LedgerBusinessDayPolicy businessDayPolicy,
            LocalDate startDate,
            LocalDate endDate,
            @Size(max = 500) String cancelUrl,
            @Size(max = 500) String memo) {
    }

    public record PauseRequest(@NotNull LocalDate from, LocalDate to) {
    }

    /**
     * 해지. {@code revertPostedAfter}에 <b>기본값이 없다</b> — 소급 해지는 이미 원장에 들어간
     * 것을 되돌리는 유일한 경로라 사람이 매번 답해야 한다.
     */
    public record EndRequest(@NotNull LocalDate endedOn, @NotNull Boolean revertPostedAfter) {
    }

    public record EndResponse(int reverted, String message) {
    }

    public record RecurringView(
            Long id,
            String name,
            LedgerRecurringKind kind,
            LedgerFlow txType,
            long amount,
            LedgerAmountType amountType,
            Long assetId,
            String assetName,
            Long counterAssetId,
            Long categoryId,
            String categoryName,
            LedgerFrequencyType freqType,
            Integer freqInterval,
            Integer freqDay,
            Integer freqMonth,
            String freqLabel,
            LedgerBusinessDayPolicy businessDayPolicy,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate pausedFrom,
            LocalDate pausedTo,
            LedgerRecurringStatus status,
            LocalDate endedOn,
            String cancelUrl,
            String memo,
            /** 다음 결제일. 종료된 항목은 {@code null}이다. */
            LocalDate nextDate,
            /** 월 환산액. 연간 구독은 ÷12다 — 1월에만 고정비가 폭증한 것처럼 보이지 않게. */
            long monthlyEquivalent) {
    }

    /** 목록은 점검 도구다(§6.6) — 합계와 신호가 항목만큼 중요하다. */
    public record RecurringListResponse(
            List<RecurringView> items,
            Stats stats,
            Signals signals,
            List<OverdueView> overdue) {
    }

    public record Stats(long monthlyFixedTotal, long yearlyTotal,
                        int subscriptionCount, int activeCount) {
    }

    /**
     * 점검 신호(§6.6). 「이거 아직도 내고 있었나」를 찾아내는 것이 목적이다.
     *
     * @param repeatedlyCorrected 연속 정정 감지(`LDG-048`). 두 회차 이상 연달아 되돌리거나
     *                            건너뛰었다면 규칙 자체가 현실과 안 맞는다는 뜻이다 —
     *                            매달 손으로 고치는 것은 해결이 아니다
     */
    public record Signals(List<PriceIncrease> priceIncreased,
                          List<TrialEnding> trialEnding,
                          List<Long> longUnchanged,
                          List<Long> noEndDate,
                          List<RepeatedCorrection> repeatedlyCorrected) {
    }

    public record RepeatedCorrection(Long recurringId, String name, int consecutive) {
    }

    public record PriceIncrease(Long recurringId, String name,
                                long from, long to, LocalDate changedOn) {
    }

    public record TrialEnding(Long recurringId, String name, LocalDate endsOn, long amount) {
    }

    /**
     * 미납 회차. <b>대시보드 상단에 상시 노출된다</b>(§6.4).
     *
     * <p>「무시」 액션이 없으므로 확정하거나 건너뛰어야만 사라진다. 눈에 거슬리는 게 목적이다.
     */
    public record OverdueView(Long recurringId, String name, LocalDate occurrenceDate,
                              long amount, long daysOverdue, String note) {
    }

    /** 금액 변경 이력 + 미발생 이력. 몇 달째 되돌리고 있는지가 여기서 보인다. */
    public record HistoryResponse(List<AmountChange> amounts, List<MissedOccurrence> missed) {
    }

    public record AmountChange(LocalDate effectiveFrom, long amount, Long changeFromAmount) {
    }

    public record MissedOccurrence(LocalDate occurrenceDate, LedgerOverrideAction action,
                                   String note) {
    }

    /** 회차 조작(API 스펙 §4). {@code occurrenceDate}는 규칙이 계산한 <b>원래</b> 예정일이다. */
    public record OccurrenceRequest(@NotNull Long recurringId,
                                    @NotNull LocalDate occurrenceDate,
                                    @NotNull LedgerOverrideAction action,
                                    Long amount,
                                    LocalDate movedTo,
                                    @Size(max = 200) String note) {
    }

    /** 미납이 실제로 빠졌다 → 그날로 옮겨 확정한다. */
    public record ConfirmRequest(@NotNull Long recurringId,
                                 @NotNull LocalDate occurrenceDate,
                                 @NotNull LocalDate actualDate,
                                 Long amount) {
    }

    public record OccurrenceView(Long recurringId,
                                 String name,
                                 LocalDate occurrenceDate,
                                 /** 실제로 잡히는 날. 옮겼거나 영업일 보정을 받았으면 다르다. */
                                 LocalDate date,
                                 long amount,
                                 LedgerOverrideAction action,
                                 boolean overdue,
                                 Long transactionId) {
    }
}
