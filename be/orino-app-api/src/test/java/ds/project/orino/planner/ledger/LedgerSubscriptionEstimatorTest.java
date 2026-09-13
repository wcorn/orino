package ds.project.orino.planner.ledger;

import ds.project.orino.planner.ledger.subscription.LedgerSubscriptionEstimator;
import ds.project.orino.planner.ledger.subscription.LedgerSubscriptionEstimator.Baseline;
import ds.project.orino.planner.ledger.subscription.LedgerSubscriptionEstimator.Flag;
import ds.project.orino.planner.ledger.subscription.LedgerSubscriptionEstimator.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 청약 인정 추정(덧붙인 명세 §3.2). 순수 계산이라 시계도 DB도 없다.
 *
 * <p>확인하는 것은 청약홈을 흉내 내는 정확도가 아니라 <b>세는 규칙 셋</b>이다 — 같은 달은 1회,
 * 달마다 상한까지, 기준 월 다음 달부터. 선납·연체는 세지 않고 드러낸다(D-16).
 */
class LedgerSubscriptionEstimatorTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    @Test
    @DisplayName("같은 달에 여러 번 넣어도 1회이고, 상한까지만 센다")
    void sameMonthCountsOnceUpToCap() {
        Result result = LedgerSubscriptionEstimator.estimate(
                new Baseline(10, 2_500_000, YearMonth.of(2026, 7)),
                Map.of(YearMonth.of(2026, 8), 500_000L),
                SEPTEMBER);

        assertThat(result.count()).isEqualTo(11);
        assertThat(result.amount()).isEqualTo(2_750_000);
        assertThat(result.months().getFirst().recognized()).isEqualTo(250_000);
        assertThat(result.months().getFirst().flag()).isEqualTo(Flag.OVER_CAP);
    }

    @Test
    @DisplayName("상한과 같은 금액은 상한 초과가 아니다")
    void exactlyCapIsNotOver() {
        Result result = LedgerSubscriptionEstimator.estimate(
                new Baseline(0, 0, YearMonth.of(2026, 7)),
                Map.of(YearMonth.of(2026, 8), 250_000L),
                SEPTEMBER);

        assertThat(result.months().getFirst().flag()).isNull();
    }

    @Test
    @DisplayName("기준 월의 입금은 이미 기준값에 들어 있어 세지 않는다")
    void baselineMonthIsNotCountedAgain() {
        Result result = LedgerSubscriptionEstimator.estimate(
                new Baseline(38, 5_300_000, YearMonth.of(2026, 8)),
                Map.of(YearMonth.of(2026, 8), 250_000L),
                SEPTEMBER);

        assertThat(result.count()).isEqualTo(38);
        assertThat(result.months()).extracting(LedgerSubscriptionEstimator.Month::month)
                .containsExactly(SEPTEMBER);
    }

    @Test
    @DisplayName("2024년 10월분까지는 10만 원, 11월분부터 25만 원이 상한이다")
    void capChangesAfterOctober2024() {
        assertThat(LedgerSubscriptionEstimator.monthlyCap(YearMonth.of(2024, 10))).isEqualTo(100_000);
        assertThat(LedgerSubscriptionEstimator.monthlyCap(YearMonth.of(2024, 11))).isEqualTo(250_000);
    }

    @Test
    @DisplayName("끝난 달에 입금이 없으면 입금 없음, 이번 달은 아직 붙이지 않는다")
    void noDepositOnlyForEndedMonths() {
        Result result = LedgerSubscriptionEstimator.estimate(
                new Baseline(0, 0, YearMonth.of(2026, 7)), Map.of(), SEPTEMBER);

        assertThat(result.months()).extracting(LedgerSubscriptionEstimator.Month::flag)
                .containsExactly(Flag.NO_DEPOSIT, null);
        assertThat(result.count()).isZero();
    }

    @Test
    @DisplayName("기준 월이 이번 달이면 기준값이 곧 추정이다")
    void baselineThroughCurrentMonth() {
        Result result = LedgerSubscriptionEstimator.estimate(
                new Baseline(41, 6_050_000, SEPTEMBER), Map.of(SEPTEMBER, 250_000L), SEPTEMBER);

        assertThat(result.count()).isEqualTo(41);
        assertThat(result.amount()).isEqualTo(6_050_000);
        assertThat(result.months()).isEmpty();
    }
}
