package ds.project.orino.planner.ledger.summary;

import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerOverrideAction;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurring;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurringStatus;
import ds.project.orino.domain.planner.ledger.entity.LedgerSettings;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;
import ds.project.orino.domain.planner.ledger.repository.LedgerAssetRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerRecurringOverrideRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerRecurringRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.planner.ledger.card.LedgerStatementService;
import ds.project.orino.planner.ledger.common.LedgerBalances;
import ds.project.orino.planner.ledger.common.LedgerBootstrap;
import ds.project.orino.planner.ledger.common.LedgerClock;
import ds.project.orino.planner.ledger.common.LedgerPeriodResolver;
import ds.project.orino.planner.ledger.common.LedgerPeriods;
import ds.project.orino.planner.ledger.transaction.LedgerTransactionService;
import ds.project.orino.planner.ledger.transaction.dto.TransactionListResponse;
import ds.project.orino.planner.ledger.upcoming.LedgerUpcomingDtos;
import ds.project.orino.planner.ledger.upcoming.LedgerUpcomingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 요약과 대시보드.
 *
 * <p>v1에서 {@code null}로 비워 두었던 <b>월말 예상 잔액·앞으로 나갈 돈·미납 건수</b>가
 * v1.5에서 채워진다 — 카드 청구서(#1262)와 정기 항목(#1263)이 생기고 나서야 셀 수 있는
 * 값들이었다.
 *
 * <p>예정 계산은 {@link LedgerUpcomingService} 하나를 부른다. 여기서 다시 세면 예정 화면과
 * 대시보드가 다른 숫자를 말하게 되고, 그건 「원장이 틀어졌다」와 구분되지 않는다.
 */
@Service
public class LedgerSummaryService {

    /** 대시보드에 띄우는 다가오는 결제 건수. 더 보려면 예정 화면으로 간다. */
    private static final int DASHBOARD_UPCOMING = 5;

    private final LedgerTransactionService transactionService;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerAssetRepository assetRepository;
    private final LedgerRecurringRepository recurringRepository;
    private final LedgerRecurringOverrideRepository overrideRepository;
    private final LedgerUpcomingService upcomingService;
    private final LedgerStatementService statementService;
    private final LedgerPeriodResolver periods;
    private final LedgerBootstrap bootstrap;
    private final LedgerClock clock;

    public LedgerSummaryService(LedgerTransactionService transactionService,
                                LedgerTransactionRepository transactionRepository,
                                LedgerAssetRepository assetRepository,
                                LedgerRecurringRepository recurringRepository,
                                LedgerRecurringOverrideRepository overrideRepository,
                                LedgerUpcomingService upcomingService,
                                LedgerStatementService statementService,
                                LedgerPeriodResolver periods,
                                LedgerBootstrap bootstrap,
                                LedgerClock clock) {
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
        this.assetRepository = assetRepository;
        this.recurringRepository = recurringRepository;
        this.overrideRepository = overrideRepository;
        this.upcomingService = upcomingService;
        this.statementService = statementService;
        this.periods = periods;
        this.bootstrap = bootstrap;
        this.clock = clock;
    }

    @Transactional
    public LedgerSummaryResponse summary(Long memberId) {
        LedgerSettings settings = bootstrap.ensureSettings(memberId);
        LedgerPeriods.Period period = periods.containing(settings, clock.today());
        TransactionListResponse.MonthTotals totals =
                transactionService.totals(memberId, period.start(), period.end());
        LedgerUpcomingService.Plan plan =
                upcomingService.plan(memberId, clock.today(), period.end());

        return new LedgerSummaryResponse(
                totals.expense() + totals.scheduledExpense(),
                totals.expense(),
                totals.scheduledExpense(),
                transactionRepository.countUncategorized(memberId),
                plan.stats().expectedBalance(),
                plan.stats().outflow(),
                overdueCount(memberId),
                new LedgerSummaryResponse.Period(period.start(), period.end()));
    }

    /**
     * 대시보드. <b>2축 요약</b>이 이 응답의 중심이다(§8.2).
     *
     * <p>「이번 달 얼마 쓰게 되나」({@code spending})와 「통장에서 얼마 빠지나」({@code cashflow})는
     * 다른 질문이다. 한 숫자로 합치면 카드로 쓴 돈이 소비에서 한 번, 대금에서 또 한 번 세어진다.
     */
    @Transactional
    public LedgerDashboardResponse dashboard(Long memberId) {
        LedgerSettings settings = bootstrap.ensureSettings(memberId);
        LedgerPeriods.Period period = periods.containing(settings, clock.today());
        TransactionListResponse.MonthTotals totals =
                transactionService.totals(memberId, period.start(), period.end());

        // 현금 축은 「이번 달 말에 얼마 남나」라서 구간 끝까지다.
        LedgerUpcomingService.Plan cashflow =
                upcomingService.plan(memberId, clock.today(), period.end());
        // 다가오는 결제는 그보다 멀리 본다 — 월말에 이 카드가 통째로 비면 화면이 고장난 것처럼
        // 보이고, 정작 사람이 알아야 할 다음 달 카드값이 어디에도 안 나온다(§8.3).
        List<LedgerUpcomingDtos.UpcomingItem> upcoming = upcomingService
                .plan(memberId, clock.today(),
                        clock.today().plusDays(LedgerUpcomingService.DEFAULT_DAYS))
                .items().stream()
                .limit(DASHBOARD_UPCOMING)
                .toList();

        return new LedgerDashboardResponse(
                new LedgerDashboardResponse.Spending(totals.expense(), totals.scheduledExpense(),
                        totals.expense() + totals.scheduledExpense()),
                new LedgerDashboardResponse.Cashflow(
                        cashflow.stats().currentBalance(), cashflow.stats().outflow(),
                        cashflow.stats().income(), cashflow.stats().expectedBalance(),
                        cashflow.stats().minBalance()),
                new LedgerDashboardResponse.Income(totals.income()),
                netWorth(memberId),
                upcoming,
                new LedgerDashboardResponse.Todo(
                        transactionRepository.countUncategorized(memberId),
                        overdueCount(memberId)),
                new LedgerDashboardResponse.Period(
                        period.start(), period.end(), settings.getMonthStartDay()));
    }

    /**
     * 미납 — 정기 회차와 카드 청구서 두 곳에서 온다.
     *
     * <p>둘 다 <b>저장된 플래그가 아니라 판정</b>이다(D-8). 물어볼 때마다 센다.
     */
    private long overdueCount(Long memberId) {
        List<Long> ruleIds = recurringRepository.findAllByMemberIdOrderByIdAsc(memberId).stream()
                .filter(rule -> rule.getStatus() != LedgerRecurringStatus.ENDED)
                .map(LedgerRecurring::getId)
                .toList();
        long unpaidOccurrences = ruleIds.isEmpty() ? 0 : overrideRepository
                .findAllByRecurringIdInAndAction(ruleIds, LedgerOverrideAction.UNPAID).size();
        return unpaidOccurrences + statementService.overdue(memberId).size();
    }

    /** 순자산 = 잔액 자산 − 부채(카드 미결제 + 할부 잔여). */
    private LedgerDashboardResponse.NetWorth netWorth(Long memberId) {
        List<LedgerAsset> assets =
                assetRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId);
        LedgerBalances balances = LedgerBalances.of(assets,
                transactionRepository.sumConfirmedByAssetAndType(
                        memberId, LedgerTransactionStatus.CONFIRMED),
                transactionRepository.sumConfirmedByCounterAsset(
                        memberId, LedgerTransactionStatus.CONFIRMED));
        return new LedgerDashboardResponse.NetWorth(
                balances.totalAssets(), balances.liabilities(), balances.netWorth());
    }
}
