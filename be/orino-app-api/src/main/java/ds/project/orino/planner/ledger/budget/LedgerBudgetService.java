package ds.project.orino.planner.ledger.budget;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.ledger.entity.LedgerBudget;
import ds.project.orino.domain.planner.ledger.entity.LedgerBudgetCategory;
import ds.project.orino.domain.planner.ledger.entity.LedgerCategory;
import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurring;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurringStatus;
import ds.project.orino.domain.planner.ledger.entity.LedgerSettings;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;
import ds.project.orino.domain.planner.ledger.repository.LedgerBudgetCategoryRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerBudgetRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerCategoryRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerRecurringRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.planner.ledger.common.LedgerBootstrap;
import ds.project.orino.planner.ledger.common.LedgerCategorySpending;
import ds.project.orino.planner.ledger.common.LedgerClock;
import ds.project.orino.planner.ledger.common.LedgerPeriodResolver;
import ds.project.orino.planner.ledger.common.LedgerPeriods;
import ds.project.orino.planner.ledger.recurring.LedgerRecurrence;
import ds.project.orino.planner.ledger.upcoming.LedgerUpcomingDtos;
import ds.project.orino.planner.ledger.upcoming.LedgerUpcomingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 예산(확정 명세 §9).
 *
 * <p><b>구간을 저장한다.</b> 월 시작일을 나중에 바꿔도 과거 예산의 구간은 그대로다 —
 * 소급해서 달라지면 「지난달 같은 시점 대비」가 거짓말이 된다.
 *
 * <p>게이지는 <b>2단</b>이다: 이미 쓴 돈(진하게)과 아직 안 썼지만 나갈 게 확정된 돈(연하게).
 * 확정분만 보여주면 「아직 절반 남았네」 하다가 25일에 고정비가 빠지고 놀란다(§8.2).
 *
 * <p><b>여행 지출은 게이지 밖이다</b>(§9 · 여행 v2.2 §5.2). 넣으면 여행 간 달은 항상 예산
 * 초과가 되고, 그러면 그 게이지는 아무것도 알려주지 않는다. 다만 <b>빼놓고 말하지는 않는다</b> —
 * {@code tripExpense}로 따로 내려 화면이 「이 달 여행으로 41만」 한 줄을 남긴다.
 */
@Service
public class LedgerBudgetService {

    private final LedgerBudgetRepository budgetRepository;
    private final LedgerBudgetCategoryRepository budgetCategoryRepository;
    private final LedgerCategoryRepository categoryRepository;
    private final LedgerRecurringRepository recurringRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerUpcomingService upcomingService;
    private final LedgerPeriodResolver periods;
    private final LedgerBootstrap bootstrap;
    private final LedgerClock clock;

    public LedgerBudgetService(LedgerBudgetRepository budgetRepository,
                               LedgerBudgetCategoryRepository budgetCategoryRepository,
                               LedgerCategoryRepository categoryRepository,
                               LedgerRecurringRepository recurringRepository,
                               LedgerTransactionRepository transactionRepository,
                               LedgerUpcomingService upcomingService,
                               LedgerPeriodResolver periods,
                               LedgerBootstrap bootstrap,
                               LedgerClock clock) {
        this.budgetRepository = budgetRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
        this.categoryRepository = categoryRepository;
        this.recurringRepository = recurringRepository;
        this.transactionRepository = transactionRepository;
        this.upcomingService = upcomingService;
        this.periods = periods;
        this.bootstrap = bootstrap;
        this.clock = clock;
    }

    /** 예산을 세우지 않은 달도 답이 있다 — 한도 0으로 「얼마 썼는지」는 보여준다. */
    @Transactional
    public LedgerBudgetDtos.BudgetResponse get(Long memberId, String period) {
        LedgerSettings settings = bootstrap.ensureSettings(memberId);
        YearMonth month = monthOf(period);
        LedgerBudget budget = budgetRepository
                .findByMemberIdAndPeriod(memberId, month.toString())
                .orElse(null);
        LedgerPeriods.Period window = budget == null
                ? periods.of(settings, month)
                // 저장된 구간을 쓴다. 설정이 그 뒤에 바뀌었어도 그때의 예산은 그때의 구간이다.
                : new LedgerPeriods.Period(budget.getPeriodStart(), budget.getPeriodEnd());
        return view(memberId, month, window, budget);
    }

    @Transactional
    public LedgerBudgetDtos.BudgetResponse put(Long memberId, String period,
                                               LedgerBudgetDtos.PutRequest request) {
        LedgerSettings settings = bootstrap.ensureSettings(memberId);
        YearMonth month = monthOf(period);
        LedgerPeriods.Period window = periods.of(settings, month);

        LedgerBudget budget = budgetRepository.findByMemberIdAndPeriod(memberId, month.toString())
                .orElseGet(() -> budgetRepository.save(new LedgerBudget(
                        memberId, month.toString(), window.start(), window.end(),
                        request.totalAmount())));
        budget.updateTotalAmount(request.totalAmount());

        // 통째로 갈아 끼운다. 「보낸 것만 바꾼다」로 두면 화면에서 지운 카테고리 한도가 남는다.
        budgetCategoryRepository.deleteAllByBudgetId(budget.getId());
        if (request.categories() != null) {
            for (LedgerBudgetDtos.CategoryAmount category : request.categories()) {
                requireCategory(memberId, category.categoryId());
                budgetCategoryRepository.save(new LedgerBudgetCategory(
                        budget.getId(), category.categoryId(), category.amount()));
            }
        }
        return view(memberId, month,
                new LedgerPeriods.Period(budget.getPeriodStart(), budget.getPeriodEnd()), budget);
    }

    private LedgerBudgetDtos.BudgetResponse view(Long memberId, YearMonth month,
                                                 LedgerPeriods.Period window,
                                                 LedgerBudget budget) {
        Map<Long, Long> limits = new HashMap<>();
        if (budget != null) {
            budgetCategoryRepository.findAllByBudgetId(budget.getId())
                    .forEach(row -> limits.put(row.getCategoryId(), row.getAmount()));
        }
        Map<Long, Long> spent = spendingByCategory(memberId, window);
        Map<Long, Long> scheduled = scheduledByCategory(memberId, window);

        long total = budget == null ? 0 : budget.getTotalAmount();
        long spentTotal = sum(spent.values());
        long scheduledTotal = sum(scheduled.values());
        long fixedCost = fixedCostTotal(memberId);
        int daysLeft = daysLeft(window);

        return new LedgerBudgetDtos.BudgetResponse(
                month.toString(), window.start(), window.end(), total,
                fixedCost, total - fixedCost, spentTotal, scheduledTotal,
                total - spentTotal - scheduledTotal, daysLeft,
                // 이미 나갈 게 확정된 돈까지 뺀 뒤 나눈다 — 안 그러면 매일 조금씩 줄어드는
                // 숫자를 믿다가 월말에 한꺼번에 터진다.
                Math.max(total - spentTotal - scheduledTotal, 0) / daysLeft,
                tripExpense(memberId, window),
                categories(memberId, limits, spent, scheduled));
    }

    /**
     * 카테고리별 지출. <b>환불은 그 카테고리의 지출을 줄인다</b>(#1261과 같은 계산).
     *
     * <p>미분류(NULL)도 한 칸을 차지한다 — 안 보이면 정리하지 않는다.
     */
    private Map<Long, Long> spendingByCategory(Long memberId, LedgerPeriods.Period window) {
        Map<Long, Long> byCategory = new HashMap<>();
        for (LedgerCategorySpending.Bucket bucket : LedgerCategorySpending.netExpense(
                transactionRepository.sumByCategoryAndFlowExcludingTrip(memberId,
                        LedgerTransactionStatus.CONFIRMED, window.start(), window.end()))) {
            byCategory.put(bucket.categoryId(), bucket.amount());
        }
        return byCategory;
    }

    /**
     * 이 구간에 <b>여행으로</b> 쓴 돈. 게이지 아래 한 줄이 되는 값이다(여행 v2.2 §5.2).
     *
     * <p><b>가계부가 자기 데이터로 센다</b> — 여행 모듈을 호출하지 않는다(아키텍처 §11.1).
     * 조건은 그 구간의 {@code trip_id IS NOT NULL} 지출 합 하나뿐이고, 그래서 여행을
     * 나중에 지워 {@code trip_id}가 NULL이 되면 이 값도 함께 사라진다 — 그게 맞다.
     *
     * <p>환불·이체 규칙은 게이지와 <b>같다</b>. 달라지면 둘을 더해도 원래 합계가 안 나온다.
     */
    private long tripExpense(Long memberId, LedgerPeriods.Period window) {
        return LedgerCategorySpending.total(LedgerCategorySpending.netExpense(
                transactionRepository.sumByCategoryAndFlowOnTrip(memberId,
                        LedgerTransactionStatus.CONFIRMED, window.start(), window.end())));
    }

    /**
     * 게이지의 <b>연한</b> 부분 — 아직 안 썼지만 나갈 게 확정된 지출.
     *
     * <p>예정 목록(4출처)에서 가져온다. 직접 예약만 세면 정기 항목이 통째로 빠지고, 그러면
     * 「아직 절반 남았네」 하다가 25일에 고정비가 나가고 놀란다.
     *
     * <p><b>지출만 센다.</b> 카드 대금과 할부는 이체라 소비 축에 들어가지 않는다 —
     * 예산은 「이번 달 얼마 쓰나」에 걸린 축이다(§8.2).
     */
    private Map<Long, Long> scheduledByCategory(Long memberId, LedgerPeriods.Period window) {
        LocalDate from = clock.today().isAfter(window.start()) ? clock.today() : window.start();
        Map<Long, Long> byCategory = new HashMap<>();
        if (from.isAfter(window.end())) {
            return byCategory;
        }
        for (LedgerUpcomingDtos.UpcomingItem item
                : upcomingService.plan(memberId, from, window.end()).items()) {
            if (item.flow() != LedgerFlow.EXPENSE) {
                continue;
            }
            // 여행에 붙은 예약도 게이지 밖이다. 확정분만 빼면 여행 지출을 미리 적어 둔
            // 달의 게이지가 여전히 여행 때문에 찬다.
            if (item.tripId() != null) {
                continue;
            }
            byCategory.merge(item.categoryId(), item.amount(), Long::sum);
        }
        return byCategory;
    }

    private List<LedgerBudgetDtos.CategoryProgress> categories(Long memberId,
                                                               Map<Long, Long> limits,
                                                               Map<Long, Long> spent,
                                                               Map<Long, Long> scheduled) {
        Map<Long, String> names = new LinkedHashMap<>();
        categoryRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId)
                .forEach(category -> names.put(category.getId(), category.getName()));

        List<LedgerBudgetDtos.CategoryProgress> result = new ArrayList<>();
        for (Map.Entry<Long, String> entry : names.entrySet()) {
            Long id = entry.getKey();
            long limit = limits.getOrDefault(id, 0L);
            long used = spent.getOrDefault(id, 0L);
            long coming = scheduled.getOrDefault(id, 0L);
            // 한도도 없고 쓴 적도 없는 카테고리는 줄을 차지하지 않는다.
            if (limit == 0 && used == 0 && coming == 0) {
                continue;
            }
            result.add(new LedgerBudgetDtos.CategoryProgress(
                    id, entry.getValue(), limit, used, coming));
        }
        long uncategorized = spent.getOrDefault(null, 0L);
        long uncategorizedScheduled = scheduled.getOrDefault(null, 0L);
        if (uncategorized != 0 || uncategorizedScheduled != 0) {
            result.add(new LedgerBudgetDtos.CategoryProgress(
                    null, "미분류", 0, uncategorized, uncategorizedScheduled));
        }
        return result;
    }

    /**
     * 고정비 자동 반영 — 정기 항목 지출의 월 환산 합(§9).
     *
     * <p>「쓸 수 있는 돈」에서 미리 빼 두지 않으면, 매달 나가기로 돼 있는 돈까지 쓸 수 있다고
     * 착각하게 된다.
     */
    private long fixedCostTotal(Long memberId) {
        long sum = 0;
        for (LedgerRecurring rule : recurringRepository.findAllByMemberIdOrderByIdAsc(memberId)) {
            if (rule.getStatus() == LedgerRecurringStatus.ENDED
                    || rule.getTxType() != LedgerFlow.EXPENSE) {
                continue;
            }
            sum += LedgerRecurrence.monthlyEquivalent(rule);
        }
        return sum;
    }

    /** 남은 일수. 지난 구간을 보면 1이다 — 0으로 나누지 않기 위해서이기도 하다. */
    private int daysLeft(LedgerPeriods.Period window) {
        LocalDate today = clock.today();
        if (today.isBefore(window.start())) {
            return (int) ChronoUnit.DAYS.between(window.start(), window.end()) + 1;
        }
        long left = ChronoUnit.DAYS.between(today, window.end()) + 1;
        return (int) Math.max(left, 1);
    }

    private YearMonth monthOf(String period) {
        if (period == null || period.isBlank()) {
            return YearMonth.from(clock.today());
        }
        return YearMonth.parse(period);
    }

    private LedgerCategory requireCategory(Long memberId, Long categoryId) {
        return categoryRepository.findByIdAndMemberId(categoryId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_CATEGORY_NOT_FOUND));
    }

    private long sum(Iterable<Long> values) {
        long total = 0;
        for (Long value : values) {
            total += value;
        }
        return total;
    }
}
