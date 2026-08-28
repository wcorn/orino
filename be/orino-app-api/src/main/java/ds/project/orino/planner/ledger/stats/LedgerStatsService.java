package ds.project.orino.planner.ledger.stats;

import ds.project.orino.domain.planner.ledger.entity.LedgerCategory;
import ds.project.orino.domain.planner.ledger.entity.LedgerSettings;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;
import ds.project.orino.domain.planner.ledger.repository.LedgerCategoryRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.planner.ledger.common.LedgerBootstrap;
import ds.project.orino.planner.ledger.common.LedgerCategorySpending;
import ds.project.orino.planner.ledger.common.LedgerClock;
import ds.project.orino.planner.ledger.common.LedgerPeriods;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 카테고리 통계(`LDG-081`).
 *
 * <p>비교 대상은 <b>지난 구간</b>과 <b>작년 같은 구간</b>이다. 「이번 달 많이 썼나」는 혼자서는
 * 답할 수 없는 질문이라, 숫자 하나만 주면 화면이 그 판단을 사용자에게 떠넘기게 된다.
 */
@Service
public class LedgerStatsService {

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerCategoryRepository categoryRepository;
    private final LedgerBootstrap bootstrap;
    private final LedgerClock clock;

    public LedgerStatsService(LedgerTransactionRepository transactionRepository,
                              LedgerCategoryRepository categoryRepository,
                              LedgerBootstrap bootstrap,
                              LedgerClock clock) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.bootstrap = bootstrap;
        this.clock = clock;
    }

    /** {@code month}가 없으면 지금 속한 구간이다. */
    @Transactional
    public LedgerStatsResponse stats(Long memberId, YearMonth month) {
        LedgerSettings settings = bootstrap.ensureSettings(memberId);
        int startDay = settings.getMonthStartDay();

        LedgerPeriods.Period period = month == null
                ? LedgerPeriods.containing(clock.today(), startDay)
                : LedgerPeriods.of(month, startDay);
        YearMonth label = YearMonth.from(period.start());

        List<LedgerCategorySpending.Bucket> buckets = spendingIn(memberId, period);
        long total = LedgerCategorySpending.total(buckets);

        Map<Long, String> names = new HashMap<>();
        for (LedgerCategory category : categoryRepository
                .findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId)) {
            names.put(category.getId(), category.getName());
        }

        List<LedgerStatsResponse.CategoryStat> byCategory = new ArrayList<>();
        for (LedgerCategorySpending.Bucket bucket : buckets) {
            byCategory.add(new LedgerStatsResponse.CategoryStat(
                    bucket.categoryId(),
                    bucket.categoryId() == null ? null : names.get(bucket.categoryId()),
                    bucket.amount(),
                    bucket.count(),
                    // 0으로 나누지 않는다. 아무것도 안 썼으면 비율도 없다.
                    total == 0 ? 0 : (double) bucket.amount() / total));
        }

        return new LedgerStatsResponse(
                new LedgerStatsResponse.Period(period.start(), period.end(), label.toString()),
                total,
                byCategory,
                new LedgerStatsResponse.Comparison(
                        bucketFor(memberId, LedgerPeriods.of(label.minusMonths(1), startDay), total),
                        bucketFor(memberId, LedgerPeriods.of(label.minusYears(1), startDay), total)));
    }

    private LedgerStatsResponse.Comparison.Bucket bucketFor(Long memberId,
                                                            LedgerPeriods.Period period,
                                                            long current) {
        long total = LedgerCategorySpending.total(spendingIn(memberId, period));
        return new LedgerStatsResponse.Comparison.Bucket(
                period.start(), period.end(), total, current - total);
    }

    private List<LedgerCategorySpending.Bucket> spendingIn(Long memberId,
                                                           LedgerPeriods.Period period) {
        return LedgerCategorySpending.netExpense(
                transactionRepository.sumByCategoryAndFlow(
                        memberId, LedgerTransactionStatus.CONFIRMED,
                        period.start(), period.end()));
    }
}
