package ds.project.orino.planner.ledger.summary;

import ds.project.orino.domain.planner.ledger.entity.LedgerSettings;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.planner.ledger.common.LedgerBootstrap;
import ds.project.orino.planner.ledger.common.LedgerClock;
import ds.project.orino.planner.ledger.common.LedgerPeriods;
import ds.project.orino.planner.ledger.transaction.LedgerTransactionService;
import ds.project.orino.planner.ledger.transaction.dto.TransactionListResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


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
        LedgerPeriods.Period period =
                LedgerPeriods.containing(clock.today(), settings.getMonthStartDay());

        TransactionListResponse.MonthTotals totals =
                transactionService.totals(memberId, period.start(), period.end());

        return new LedgerSummaryResponse(
                totals.expense() + totals.scheduledExpense(),
                totals.expense(),
                totals.scheduledExpense(),
                transactionRepository.countUncategorized(memberId),
                null,
                null,
                null,
                new LedgerSummaryResponse.Period(period.start(), period.end()));
    }

    /**
     * 대시보드. v1은 <b>이미 쓴 돈 · 이번 달 수입 · 정리할 내역</b> 셋이다.
     *
     * <p>2축 요약·미납 경고·다가오는 결제를 <b>빈 값으로 내려보내지 않는다</b>(D-7).
     * 화면이 그 자리를 비워 두면 고장난 것처럼 보이고, 0으로 채우면 「없다」는 거짓말이 된다.
     * 값이 아니라 <b>필드 자체가 없다</b> — v1.5에서 생긴다.
     */
    @Transactional
    public LedgerDashboardResponse dashboard(Long memberId) {
        LedgerSettings settings = bootstrap.ensureSettings(memberId);
        LedgerPeriods.Period period =
                LedgerPeriods.containing(clock.today(), settings.getMonthStartDay());

        TransactionListResponse.MonthTotals totals =
                transactionService.totals(memberId, period.start(), period.end());

        return new LedgerDashboardResponse(
                new LedgerDashboardResponse.Spending(totals.expense()),
                new LedgerDashboardResponse.Income(totals.income()),
                new LedgerDashboardResponse.Todo(
                        transactionRepository.countUncategorized(memberId)),
                new LedgerDashboardResponse.Period(
                        period.start(), period.end(), settings.getMonthStartDay()));
    }
}
