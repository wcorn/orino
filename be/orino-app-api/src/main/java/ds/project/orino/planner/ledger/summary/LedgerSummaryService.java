package ds.project.orino.planner.ledger.summary;

import ds.project.orino.domain.planner.ledger.entity.LedgerSettings;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.planner.ledger.common.LedgerBootstrap;
import ds.project.orino.planner.ledger.common.LedgerClock;
import ds.project.orino.planner.ledger.transaction.LedgerTransactionService;
import ds.project.orino.planner.ledger.transaction.dto.TransactionListResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 요약. v1이 답할 수 있는 것만 채우고 나머지는 {@code null}로 둔다.
 *
 * <p>월말 예상 잔액·앞으로 나갈 돈·미납 건수는 <b>카드 청구서와 정기 항목이 있어야</b>
 * 계산된다(v1.5 · #1264). 지금 0으로 채워 두면 화면은 「미납 없음」을 그리고, 그건 사실이
 * 아니라 <b>아직 셀 수 없다</b>는 뜻이다.
 */
@Service
public class LedgerSummaryService {

    private final LedgerTransactionService transactionService;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerBootstrap bootstrap;
    private final LedgerClock clock;

    public LedgerSummaryService(LedgerTransactionService transactionService,
                                LedgerTransactionRepository transactionRepository,
                                LedgerBootstrap bootstrap,
                                LedgerClock clock) {
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
        this.bootstrap = bootstrap;
        this.clock = clock;
    }

    @Transactional
    public LedgerSummaryResponse summary(Long memberId) {
        LedgerSettings settings = bootstrap.ensureSettings(memberId);
        LocalDate today = clock.today();
        LocalDate start = periodStart(today, settings.getMonthStartDay());
        LocalDate end = periodEnd(start, settings.getMonthStartDay());

        TransactionListResponse.MonthTotals totals =
                transactionService.totals(memberId, start, end);

        return new LedgerSummaryResponse(
                totals.expense() + totals.scheduledExpense(),
                totals.expense(),
                totals.scheduledExpense(),
                transactionRepository.countUncategorized(memberId),
                null,
                null,
                null,
                new LedgerSummaryResponse.Period(start, end));
    }

    /**
     * 이번 달 구간의 시작. 월 시작일이 25일이면 8월 20일은 아직 <b>7월 25일 시작 구간</b>이다.
     */
    private LocalDate periodStart(LocalDate today, int monthStartDay) {
        if (monthStartDay == LedgerSettings.LAST_DAY_OF_MONTH) {
            LocalDate thisMonthLastDay = today.withDayOfMonth(today.lengthOfMonth());
            return today.isBefore(thisMonthLastDay)
                    ? lastDayOf(today.minusMonths(1))
                    : thisMonthLastDay;
        }
        LocalDate candidate = today.withDayOfMonth(Math.min(monthStartDay, today.lengthOfMonth()));
        return today.isBefore(candidate) ? candidate.minusMonths(1) : candidate;
    }

    private LocalDate periodEnd(LocalDate start, int monthStartDay) {
        if (monthStartDay == LedgerSettings.LAST_DAY_OF_MONTH) {
            return lastDayOf(start.plusMonths(1)).minusDays(1);
        }
        return start.plusMonths(1).minusDays(1);
    }

    private LocalDate lastDayOf(LocalDate date) {
        return date.withDayOfMonth(date.lengthOfMonth());
    }
}
