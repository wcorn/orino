package ds.project.orino.planner.ledger.recurring;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.ledger.entity.LedgerOverrideAction;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurring;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurringOverride;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransaction;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.planner.ledger.common.LedgerClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 회차 하나를 손댄다 — 금액·건너뛰기·이동·미납·되돌리기(API 스펙 §4).
 *
 * <p>손댄 회차만 {@code ledger_recurring_override} 1행이 남는다(D-5). 규칙을 고치는 것과
 * 다른 일이다 — 규칙 수정은 앞으로의 모든 회차를 바꾸고, 여기는 <b>이번 회차만</b> 바꾼다.
 *
 * <p><b>「무시」에 해당하는 행위는 없다.</b> 미납은 확정하거나 건너뛰어야만 사라진다 —
 * 안 낸 돈은 여전히 내야 할 돈이고, 눈에 거슬리는 게 목적이다(확정 명세 §6.4).
 */
@Service
public class LedgerOccurrenceService {

    private final LedgerRecurringService recurringService;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerRecurringPoster poster;
    private final LedgerOccurrenceResolver resolver;
    private final LedgerClock clock;

    public LedgerOccurrenceService(LedgerRecurringService recurringService,
                                   LedgerTransactionRepository transactionRepository,
                                   LedgerRecurringPoster poster,
                                   LedgerOccurrenceResolver resolver,
                                   LedgerClock clock) {
        this.recurringService = recurringService;
        this.transactionRepository = transactionRepository;
        this.poster = poster;
        this.resolver = resolver;
        this.clock = clock;
    }

    /**
     * 회차 조작. 이미 적힌 회차면 원장도 함께 정정된다 — 「자동」은 대기 상태가 아니라
     * <b>이미 적힌 것</b>이므로, 표시만 바꾸고 원장을 두면 두 말이 어긋난다.
     */
    @Transactional
    public LedgerRecurringDtos.OccurrenceView apply(
            Long memberId, LedgerRecurringDtos.OccurrenceRequest request) {
        LedgerRecurring rule = recurringService.require(memberId, request.recurringId());
        requireOccurrence(rule, request.occurrenceDate());
        validate(request);

        LedgerRecurringOverride override = recurringService.markOverride(
                rule.getId(), request.occurrenceDate(), request.action(),
                request.amount(), request.movedTo(), request.note());

        Optional<LedgerTransaction> posted = livePosting(rule.getId(), request.occurrenceDate());
        if (request.action().removesPosting()) {
            // 장부에서 뺀다. 행은 남는다 — 미납이 뒤늦게 빠지면 이 행을 되살린다.
            posted.ifPresent(tx -> tx.softDelete(clock.now()));
        } else if (request.action() == LedgerOverrideAction.AMOUNT) {
            posted.ifPresent(tx -> tx.updateAmount(request.amount()));
        } else if (request.action() == LedgerOverrideAction.MOVE) {
            posted.ifPresent(tx -> tx.updateOccurredOn(request.movedTo()));
        }
        return view(rule, override, livePosting(rule.getId(), request.occurrenceDate()));
    }

    /**
     * 미납이 실제로 빠졌다 → <b>실제 출금일로 옮겨 확정</b>한다(§6.4).
     *
     * <p>미납 표시가 걷히고 그 회차는 옮긴 날짜에 자리 잡는다. 며칠 늦게 빠진 것을 새 거래로
     * 적으면 「이번 달에 두 번 냈다」가 되고, 그건 사실이 아니다.
     */
    @Transactional
    public LedgerRecurringDtos.OccurrenceView confirm(
            Long memberId, LedgerRecurringDtos.ConfirmRequest request) {
        LedgerRecurring rule = recurringService.require(memberId, request.recurringId());
        // 미납으로 표시된 회차만 확정할 수 있다. 「손대지 않은 회차」는 스케줄러가 적는다 —
        // 여기로 들어오면 같은 회차가 두 경로로 적히는 문이 열린다.
        LedgerRecurringOverride override = recurringService
                .findOverride(rule.getId(), request.occurrenceDate())
                .filter(row -> row.getAction() == LedgerOverrideAction.UNPAID)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.LEDGER_RECURRING_INVALID_RULE));
        long amount = amountOf(request, override, rule);

        LedgerTransaction tx = transactionRepository
                .findByRecurringIdAndOccurrenceDate(rule.getId(), request.occurrenceDate())
                .orElse(null);
        if (tx == null) {
            // 한 번도 안 적힌 회차였다(예정 단계에서 미납으로 표시해 둔 경우).
            tx = poster.post(rule, request.occurrenceDate(), request.actualDate(),
                    amount, clock.today());
        } else {
            tx.restore();
            tx.updateStatus(LedgerTransactionStatus.CONFIRMED);
            tx.updateOccurredOn(request.actualDate());
            tx.updateAmount(amount);
        }
        override.confirmAt(request.actualDate(), amount);
        return view(rule, override, Optional.ofNullable(tx));
    }

    private long amountOf(LedgerRecurringDtos.ConfirmRequest request,
                          LedgerRecurringOverride override, LedgerRecurring rule) {
        if (request.amount() != null) {
            return request.amount();
        }
        return override.getAmount() == null ? rule.getAmount() : override.getAmount();
    }

    /** 살아 있는 기록만. 되돌린 회차의 행은 남아 있지만 장부에는 없다. */
    private Optional<LedgerTransaction> livePosting(Long recurringId, LocalDate occurrenceDate) {
        return transactionRepository
                .findByRecurringIdAndOccurrenceDate(recurringId, occurrenceDate)
                .filter(tx -> !tx.isDeleted());
    }

    /**
     * 규칙이 실제로 내는 날짜인가.
     *
     * <p>이 검사가 없으면 아무 날짜로나 override를 만들 수 있고, 그러면 규칙에 없는 회차가
     * 예정과 미납 경고에 유령처럼 남는다 — 손댄 회차만 저장하기로 한 이상(D-5) 「손댈 수 있는
     * 회차」의 범위는 규칙이 정해야 한다.
     */
    private void requireOccurrence(LedgerRecurring rule, LocalDate occurrenceDate) {
        if (LedgerRecurrence.occurrences(rule, occurrenceDate, occurrenceDate).isEmpty()) {
            throw new CustomException(ErrorCode.LEDGER_RECURRING_INVALID_RULE);
        }
    }

    private void validate(LedgerRecurringDtos.OccurrenceRequest request) {
        boolean invalid = switch (request.action()) {
            case AMOUNT -> request.amount() == null || request.amount() <= 0;
            case MOVE -> request.movedTo() == null;
            case SKIP, UNPAID, REVERTED -> false;
        };
        if (invalid) {
            throw new CustomException(ErrorCode.LEDGER_RECURRING_INVALID_RULE);
        }
    }

    private LedgerRecurringDtos.OccurrenceView view(LedgerRecurring rule,
                                                    LedgerRecurringOverride override,
                                                    Optional<LedgerTransaction> posted) {
        long amount = override.getAmount() == null ? rule.getAmount() : override.getAmount();
        LocalDate date = override.getMovedTo() != null
                ? override.getMovedTo() : resolver.adjust(rule, override.getOccurrenceDate());
        return new LedgerRecurringDtos.OccurrenceView(
                rule.getId(), rule.getName(), override.getOccurrenceDate(), date, amount,
                override.getAction(),
                override.getAction() == LedgerOverrideAction.UNPAID,
                posted.map(LedgerTransaction::getId).orElse(null));
    }
}
