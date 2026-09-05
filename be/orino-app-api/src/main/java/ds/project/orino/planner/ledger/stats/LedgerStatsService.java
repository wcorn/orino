package ds.project.orino.planner.ledger.stats;

import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerCategory;
import ds.project.orino.domain.planner.ledger.entity.LedgerCostType;
import ds.project.orino.domain.planner.ledger.entity.LedgerPerspective;
import ds.project.orino.domain.planner.ledger.entity.LedgerSettings;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;
import ds.project.orino.domain.planner.ledger.repository.LedgerAssetRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerCategoryRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.planner.ledger.common.LedgerBalances;
import ds.project.orino.planner.ledger.common.LedgerBootstrap;
import ds.project.orino.planner.ledger.common.LedgerCategorySpending;
import ds.project.orino.planner.ledger.common.LedgerClock;
import ds.project.orino.planner.ledger.common.LedgerPeriods;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 통계(`LDG-081`·`LDG-082`·`LDG-086`).
 *
 * <p>비교 대상은 <b>지난 구간</b>과 <b>작년 같은 구간</b>이다. 「이번 달 많이 썼나」는 혼자서는
 * 답할 수 없는 질문이라, 숫자 하나만 주면 화면이 그 판단을 사용자에게 떠넘기게 된다.
 *
 * <p>v2에서 셋이 붙었다 — <b>관점 전환</b>(그리고 두 관점이 벌어지는 이유), <b>고정 대 변동</b>,
 * <b>연간 결산</b>. 셋 다 「절약 여지가 어디에 있나」라는 한 질문의 다른 각도다.
 */
@Service
public class LedgerStatsService {

    /** 월별 추이·연간 결산이 훑는 개월 수. 열두 달이면 계절성이 한 바퀴 돈다. */
    private static final int TREND_MONTHS = 12;

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerCategoryRepository categoryRepository;
    private final LedgerAssetRepository assetRepository;
    private final LedgerPerspectiveSpending perspectiveSpending;
    private final LedgerBootstrap bootstrap;
    private final LedgerClock clock;

    public LedgerStatsService(LedgerTransactionRepository transactionRepository,
                              LedgerCategoryRepository categoryRepository,
                              LedgerAssetRepository assetRepository,
                              LedgerPerspectiveSpending perspectiveSpending,
                              LedgerBootstrap bootstrap,
                              LedgerClock clock) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.assetRepository = assetRepository;
        this.perspectiveSpending = perspectiveSpending;
        this.bootstrap = bootstrap;
        this.clock = clock;
    }

    /**
     * {@code month}가 없으면 지금 속한 구간, {@code perspective}가 없으면 설정의 기본값이다.
     *
     * <p>{@code excludeTrip}은 <b>화면 전체에 걸린다</b> — 카테고리·자산·고정변동·월별 추이·
     * 연간 결산·기간 비교가 모두 같은 렌즈를 쓴다. 한쪽만 거르면 상단 합계와 아래 막대가 다른
     * 이야기를 하고, 사용자는 어느 쪽이 맞는지 알 방법이 없다.
     */
    @Transactional
    public LedgerStatsResponse stats(Long memberId, YearMonth month,
                                     LedgerPerspective requested, boolean excludeTrip) {
        LedgerSettings settings = bootstrap.ensureSettings(memberId);
        int startDay = settings.getMonthStartDay();
        LedgerPerspective perspective = requested == null
                ? settings.getDefaultPerspective() : requested;

        LedgerPeriods.Period period = month == null
                ? LedgerPeriods.containing(clock.today(), startDay)
                : LedgerPeriods.of(month, startDay);
        YearMonth label = YearMonth.from(period.start());

        List<LedgerCategory> categories =
                categoryRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId);
        Map<Long, LedgerCategory> categoryById = new HashMap<>();
        categories.forEach(category -> categoryById.put(category.getId(), category));

        List<LedgerAsset> assets =
                assetRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId);
        List<LedgerCategorySpending.Bucket> buckets =
                perspectiveSpending.byCategory(memberId, period, perspective, excludeTrip);
        long total = LedgerCategorySpending.total(buckets);

        return new LedgerStatsResponse(
                new LedgerStatsResponse.Period(period.start(), period.end(), label.toString()),
                perspective,
                excludeTrip,
                total,
                categoryStats(buckets, categoryById, total),
                assetStats(memberId, assets, period, perspective, total, excludeTrip),
                fixedVsVariable(buckets, categoryById),
                monthly(memberId, label, startDay, categoryById, assets, excludeTrip),
                settlement(memberId, label, startDay, categoryById, excludeTrip),
                new LedgerStatsResponse.Comparison(
                        bucketFor(memberId, LedgerPeriods.of(label.minusMonths(1), startDay),
                                perspective, total, excludeTrip),
                        bucketFor(memberId, LedgerPeriods.of(label.minusYears(1), startDay),
                                perspective, total, excludeTrip)),
                perspectiveDiff(memberId, period, perspective, total, excludeTrip));
    }

    private List<LedgerStatsResponse.CategoryStat> categoryStats(
            List<LedgerCategorySpending.Bucket> buckets,
            Map<Long, LedgerCategory> categoryById,
            long total) {
        List<LedgerStatsResponse.CategoryStat> stats = new ArrayList<>();
        for (LedgerCategorySpending.Bucket bucket : buckets) {
            LedgerCategory category = bucket.categoryId() == null
                    ? null : categoryById.get(bucket.categoryId());
            stats.add(new LedgerStatsResponse.CategoryStat(
                    bucket.categoryId(),
                    category == null ? null : category.getName(),
                    bucket.amount(),
                    bucket.count(),
                    // 0으로 나누지 않는다. 아무것도 안 썼으면 비율도 없다.
                    total == 0 ? 0 : (double) bucket.amount() / total));
        }
        return stats;
    }

    /**
     * 자산별 지출(`LDG-082`). 대금 결제는 이체라 여기 없다 — 세면 두 번 잡힌다.
     *
     * <p><b>관점을 따라간다.</b> 카테고리·고정변동과 같은 합계를 나눠 갖지 않으면 비율의
     * 분모가 제 것이 아니게 된다 — 청구 기준에서 카드 사용을 그대로 두면 합계에 없는 돈이
     * 88%를 차지하는 줄이 생기고, 막대는 칸을 넘어간다.
     */
    private List<LedgerStatsResponse.AssetStat> assetStats(Long memberId,
                                                           List<LedgerAsset> assets,
                                                           LedgerPeriods.Period period,
                                                           LedgerPerspective perspective,
                                                           long total,
                                                           boolean excludeTrip) {
        Map<Long, String> names = new HashMap<>();
        for (LedgerAsset asset : assets) {
            names.put(asset.getId(), asset.getName());
        }

        List<LedgerStatsResponse.AssetStat> stats = new ArrayList<>();
        for (LedgerCategorySpending.Bucket bucket
                : perspectiveSpending.byAsset(memberId, period, perspective, excludeTrip)) {
            stats.add(new LedgerStatsResponse.AssetStat(
                    bucket.categoryId(), names.get(bucket.categoryId()), bucket.amount(),
                    total == 0 ? 0 : (double) bucket.amount() / total));
        }
        stats.sort(Comparator.comparingLong(LedgerStatsResponse.AssetStat::amount).reversed());
        return stats;
    }

    /**
     * 고정 대 변동.
     *
     * <p>속성을 안 정한 카테고리는 <b>따로 센다</b> — 변동비에 몰아넣으면 아무도 분류하지 않은
     * 가계부에서 「변동비가 100%」라는 거짓말이 나온다.
     */
    private LedgerStatsResponse.FixedVsVariable fixedVsVariable(
            List<LedgerCategorySpending.Bucket> buckets,
            Map<Long, LedgerCategory> categoryById) {
        long fixed = 0;
        long variable = 0;
        long unclassified = 0;
        for (LedgerCategorySpending.Bucket bucket : buckets) {
            LedgerCostType type = costTypeOf(bucket.categoryId(), categoryById);
            if (type == LedgerCostType.FIXED) {
                fixed += bucket.amount();
            } else if (type == LedgerCostType.VARIABLE) {
                variable += bucket.amount();
            } else {
                unclassified += bucket.amount();
            }
        }
        return new LedgerStatsResponse.FixedVsVariable(fixed, variable, unclassified);
    }

    /** 최근 열두 달. 연간 결산 막대와 고정/변동 추이가 이 배열 하나를 함께 읽는다. */
    private List<LedgerStatsResponse.MonthlyPoint> monthly(Long memberId, YearMonth label,
                                                           int startDay,
                                                           Map<Long, LedgerCategory> categoryById,
                                                           List<LedgerAsset> assets,
                                                           boolean excludeTrip) {
        List<LedgerStatsResponse.MonthlyPoint> points = new ArrayList<>();
        for (int back = TREND_MONTHS - 1; back >= 0; back--) {
            YearMonth month = label.minusMonths(back);
            LedgerPeriods.Period period = LedgerPeriods.of(month, startDay);
            List<LedgerCategorySpending.Bucket> buckets =
                    netExpenseIn(memberId, period, excludeTrip);
            LedgerStatsResponse.FixedVsVariable split = fixedVsVariable(buckets, categoryById);
            points.add(new LedgerStatsResponse.MonthlyPoint(
                    month.toString(),
                    LedgerCategorySpending.total(buckets),
                    incomeIn(memberId, period),
                    split.fixed(),
                    split.variable(),
                    split.unclassified(),
                    netWorthAt(memberId, assets, period)));
        }
        return points;
    }

    /**
     * 그 달 끝의 순자산.
     *
     * <p><b>아직 오지 않은 달은 {@code null}이다</b> — 0으로 채우면 「자산이 0원이었다」는
     * 거짓말이 되고, 막대 차트에서 바닥까지 떨어진 달로 보인다.
     *
     * <p>진행 중인 달은 <b>오늘까지</b> 센다. 월말까지 세면 아직 일어나지 않은 일이 섞이는데,
     * 예정 거래는 잔액을 바꾸지 않으므로 그건 그냥 오늘 값과 같아지거나 어긋난다.
     */
    private Long netWorthAt(Long memberId, List<LedgerAsset> assets,
                            LedgerPeriods.Period period) {
        LocalDate today = clock.today();
        if (period.start().isAfter(today)) {
            return null;
        }
        LocalDate until = period.end().isAfter(today) ? today : period.end();
        return LedgerBalances.of(assets,
                transactionRepository.sumConfirmedByAssetAndTypeUpTo(
                        memberId, LedgerTransactionStatus.CONFIRMED, until),
                transactionRepository.sumConfirmedByCounterAssetUpTo(
                        memberId, LedgerTransactionStatus.CONFIRMED, until)).netWorth();
    }

    /**
     * 연간 결산.
     *
     * <p><b>결산 제외 카테고리는 빠진다</b> — 저축·투자는 「쓴 돈」이 아니라 자산 이동이고,
     * 그걸 지출로 세면 저축을 많이 한 달이 가장 헤픈 달로 보인다.
     */
    private LedgerStatsResponse.Settlement settlement(Long memberId, YearMonth label,
                                                      int startDay,
                                                      Map<Long, LedgerCategory> categoryById,
                                                      boolean excludeTrip) {
        int year = label.getYear();
        long income = 0;
        long expense = 0;
        String highest = null;
        String lowest = null;
        long highestAmount = Long.MIN_VALUE;
        long lowestAmount = Long.MAX_VALUE;

        for (int monthValue = 1; monthValue <= 12; monthValue++) {
            YearMonth month = YearMonth.of(year, monthValue);
            LedgerPeriods.Period period = LedgerPeriods.of(month, startDay);
            long monthExpense = 0;
            for (LedgerCategorySpending.Bucket bucket
                    : netExpenseIn(memberId, period, excludeTrip)) {
                LedgerCategory category = bucket.categoryId() == null
                        ? null : categoryById.get(bucket.categoryId());
                if (category != null && category.isExcludeFromSettlement()) {
                    continue;
                }
                monthExpense += bucket.amount();
            }
            income += incomeIn(memberId, period);
            expense += monthExpense;

            // 아직 오지 않은 달(전부 0)은 최고·최저 후보가 아니다.
            if (monthExpense <= 0) {
                continue;
            }
            if (monthExpense > highestAmount) {
                highestAmount = monthExpense;
                highest = month.toString();
            }
            if (monthExpense < lowestAmount) {
                lowestAmount = monthExpense;
                lowest = month.toString();
            }
        }

        // 수입이 없으면 저축률은 0이 아니라 「셀 수 없다」다.
        Double savingRate = income <= 0 ? null : (double) (income - expense) / income;
        return new LedgerStatsResponse.Settlement(
                year, income, expense, savingRate, highest, lowest);
    }

    /**
     * 다른 관점으로 보면 얼마가 달라지는가.
     *
     * <p>이유를 함께 준다 — 벌어지는 원인은 거의 언제나 <b>할부</b>이거나 <b>사이클 경계</b>다.
     * 이유 없이 숫자만 주면 사람은 둘 중 어느 쪽을 믿을지 정할 수 없다.
     */
    private LedgerStatsResponse.PerspectiveDiff perspectiveDiff(Long memberId,
                                                                LedgerPeriods.Period period,
                                                                LedgerPerspective perspective,
                                                                long total,
                                                                boolean excludeTrip) {
        LedgerPerspective other = perspective == LedgerPerspective.SPEND
                ? LedgerPerspective.BILLING : LedgerPerspective.SPEND;
        long otherTotal = LedgerCategorySpending.total(
                perspectiveSpending.byCategory(memberId, period, other, excludeTrip));
        long diff = otherTotal - total;
        return new LedgerStatsResponse.PerspectiveDiff(
                other, otherTotal, diff, diff == 0 ? null : reasonFor(memberId, period));
    }

    /**
     * 왜 벌어지나. <b>원인이 둘이면 둘 다 말한다.</b>
     *
     * <p>로컬에서 확인하다 드러난 것이다 — 카드 사용 18만과 할부 30만이 함께 있는 달에
     * 「할부 때문」이라고만 적으니 <b>18만이 설명되지 않은 채 남았다.</b> 차이가 48만인데
     * 이유가 30만어치뿐이면 사람은 나머지를 자기가 찾아야 한다.
     */
    private String reasonFor(Long memberId, LedgerPeriods.Period period) {
        boolean hasInstallment = transactionRepository
                .existsInstallmentBetween(memberId, period.start(), period.end());
        boolean hasCardUsage = transactionRepository
                .existsCardUsageBetween(memberId, period.start(), period.end());
        if (hasInstallment && hasCardUsage) {
            return "할부와 카드 사이클 경계 때문";
        }
        return hasInstallment ? "할부 때문" : "카드 사이클 경계 때문";
    }

    /**
     * 그 구간의 소비 기준 순 지출. 월별 추이와 연간 결산이 함께 쓴다.
     *
     * <p>「여행 제외」가 켜지면 <b>이쪽도 같이 걸린다</b> — 이번 달만 거르고 추이는 그대로 두면,
     * 막대 열두 개 중 하나만 다른 규칙으로 그려진다.
     */
    private List<LedgerCategorySpending.Bucket> netExpenseIn(Long memberId,
                                                             LedgerPeriods.Period period,
                                                             boolean excludeTrip) {
        return LedgerCategorySpending.netExpense(excludeTrip
                ? transactionRepository.sumByCategoryAndFlowExcludingTrip(
                        memberId, LedgerTransactionStatus.CONFIRMED,
                        period.start(), period.end())
                : transactionRepository.sumByCategoryAndFlow(
                        memberId, LedgerTransactionStatus.CONFIRMED,
                        period.start(), period.end()));
    }

    private long incomeIn(Long memberId, LedgerPeriods.Period period) {
        return transactionRepository.sumIncome(
                memberId, LedgerTransactionStatus.CONFIRMED, period.start(), period.end());
    }

    private LedgerCostType costTypeOf(Long categoryId, Map<Long, LedgerCategory> categoryById) {
        if (categoryId == null) {
            return null;
        }
        LedgerCategory category = categoryById.get(categoryId);
        return category == null ? null : category.getCostType();
    }

    private LedgerStatsResponse.Comparison.Bucket bucketFor(Long memberId,
                                                            LedgerPeriods.Period period,
                                                            LedgerPerspective perspective,
                                                            long current,
                                                            boolean excludeTrip) {
        long total = LedgerCategorySpending.total(
                perspectiveSpending.byCategory(memberId, period, perspective, excludeTrip));
        return new LedgerStatsResponse.Comparison.Bucket(
                period.start(), period.end(), total, current - total);
    }
}
