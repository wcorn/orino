package ds.project.orino.planner.ledger;

import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerAssetType;
import ds.project.orino.planner.ledger.card.LedgerBillingCycle;
import ds.project.orino.planner.ledger.card.LedgerStatementBreakdown;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 청구액 산식과 사이클 날짜(#1262).
 *
 * <p><b>산식이 이 이슈의 전부다.</b> 여기가 틀리면 화면·통계·부채가 전부 함께 틀리고,
 * 그 사실은 카드사 청구서와 맞춰 보는 월말에야 드러난다. 스프링을 타지 않는 순수 계산으로
 * 떼어 둔 이유가 그것이다 — 빠르게, 경계까지 전부 확인할 수 있어야 한다.
 */
class LedgerStatementFormulaTest {

    @Nested
    @DisplayName("청구액 산식")
    class Formula {

        @Test
        @DisplayName("사용 합계가 그대로 청구액이 된다")
        void usageOnly() {
            LedgerStatementBreakdown breakdown =
                    LedgerStatementBreakdown.of(842000, 0, 0, 0, 0, 0, 0, 0);

            assertThat(breakdown.billed()).isEqualTo(842000);
            assertThat(breakdown.remaining()).isEqualTo(842000);
        }

        @Test
        @DisplayName("할부 회차분이 더해진다")
        void addsInstallmentRounds() {
            assertThat(LedgerStatementBreakdown.of(300000, 100000, 0, 0, 0, 0, 0, 0).billed())
                    .isEqualTo(400000);
        }

        @Test
        @DisplayName("이월 잔액이 더해진다 — 청구액에는 들어간다")
        void addsCarriedOver() {
            assertThat(LedgerStatementBreakdown.of(300000, 0, 150000, 0, 0, 0, 0, 0).billed())
                    .isEqualTo(450000);
        }

        @Test
        @DisplayName("이자·수수료가 더해진다")
        void addsInterestFee() {
            assertThat(LedgerStatementBreakdown.of(300000, 0, 0, 12000, 0, 0, 0, 0).billed())
                    .isEqualTo(312000);
        }

        @Test
        @DisplayName("환불과 할인은 빠진다")
        void subtractsRefundAndDiscount() {
            assertThat(LedgerStatementBreakdown.of(300000, 0, 0, 0, 0, 50000, 10000, 0).billed())
                    .isEqualTo(240000);
        }

        @Test
        @DisplayName("차액 조정은 양쪽으로 움직인다")
        void adjustmentGoesBothWays() {
            assertThat(LedgerStatementBreakdown.of(300000, 0, 0, 0, 15000, 0, 0, 0).billed())
                    .isEqualTo(315000);
            assertThat(LedgerStatementBreakdown.of(300000, 0, 0, 0, -15000, 0, 0, 0).billed())
                    .isEqualTo(285000);
        }

        @Test
        @DisplayName("일곱 항목이 한 번에 맞아떨어진다")
        void allTermsTogether() {
            // 사용 842,000 + 할부 100,000 + 이월 150,000 + 수수료 12,000 + 차액 5,000
            //  − 환불 30,000 − 할인 20,000 = 1,059,000
            LedgerStatementBreakdown breakdown = LedgerStatementBreakdown.of(
                    842000, 100000, 150000, 12000, 5000, 30000, 20000, 0);

            assertThat(breakdown.billed()).isEqualTo(1059000);
        }

        @Test
        @DisplayName("부분 납부하면 남은 금액이 잔액이다")
        void partialPaymentLeavesRemainder() {
            LedgerStatementBreakdown breakdown =
                    LedgerStatementBreakdown.of(842000, 0, 0, 0, 0, 0, 0, 500000);

            assertThat(breakdown.remaining()).isEqualTo(342000);
        }

        @Test
        @DisplayName("더 냈어도 남은 금액이 음수가 되지 않는다 — 「돌려받을 돈」으로 읽힌다")
        void overpaymentDoesNotGoNegative() {
            LedgerStatementBreakdown breakdown =
                    LedgerStatementBreakdown.of(100000, 0, 0, 0, 0, 0, 0, 120000);

            assertThat(breakdown.remaining()).isZero();
        }
    }

    @Nested
    @DisplayName("사이클 날짜")
    class Cycle {

        /** 1일~말일 사용 → 익월 14일 결제. 가장 흔한 형태다. */
        private LedgerAsset monthlyCard() {
            LedgerAsset card = new LedgerAsset(1L, "신한 Deep Dream", LedgerAssetType.CREDIT_CARD);
            card.updateBillingCycle(1, 99, 14, null, null);
            return card;
        }

        @Test
        @DisplayName("1일~말일 사용은 익월 14일에 빠진다")
        void monthlyCycle() {
            LedgerBillingCycle.Cycle cycle =
                    LedgerBillingCycle.covering(monthlyCard(), LocalDate.parse("2026-08-15"));

            assertThat(cycle.start()).isEqualTo(LocalDate.parse("2026-08-01"));
            assertThat(cycle.end()).isEqualTo(LocalDate.parse("2026-08-31"));
            assertThat(cycle.paymentDate()).isEqualTo(LocalDate.parse("2026-09-14"));
        }

        @Test
        @DisplayName("말일에 쓴 것도 그 달 사이클이다")
        void lastDayBelongsToSameCycle() {
            LedgerBillingCycle.Cycle cycle =
                    LedgerBillingCycle.covering(monthlyCard(), LocalDate.parse("2026-08-31"));

            assertThat(cycle.start()).isEqualTo(LocalDate.parse("2026-08-01"));
        }

        @Test
        @DisplayName("2월은 짧다 — 말일이 28일이어도 사이클이 성립한다")
        void februaryIsShort() {
            LedgerBillingCycle.Cycle cycle =
                    LedgerBillingCycle.covering(monthlyCard(), LocalDate.parse("2026-02-10"));

            assertThat(cycle.end()).isEqualTo(LocalDate.parse("2026-02-28"));
            assertThat(cycle.paymentDate()).isEqualTo(LocalDate.parse("2026-03-14"));
        }

        @Test
        @DisplayName("마감일이 시작일보다 앞이면 사이클이 다음 달로 넘어간다")
        void cycleSpansTwoMonths() {
            LedgerAsset card = new LedgerAsset(1L, "국민", LedgerAssetType.CREDIT_CARD);
            // 15일 시작 · 14일 마감 = 15일~익월 14일.
            card.updateBillingCycle(15, 14, 25, null, null);

            LedgerBillingCycle.Cycle cycle =
                    LedgerBillingCycle.covering(card, LocalDate.parse("2026-08-20"));

            assertThat(cycle.start()).isEqualTo(LocalDate.parse("2026-08-15"));
            assertThat(cycle.end()).isEqualTo(LocalDate.parse("2026-09-14"));
            assertThat(cycle.paymentDate()).isEqualTo(LocalDate.parse("2026-10-25"));
        }

        @Test
        @DisplayName("시작일 직전에 쓴 건은 지난 사이클이다")
        void beforeStartBelongsToPreviousCycle() {
            LedgerAsset card = new LedgerAsset(1L, "국민", LedgerAssetType.CREDIT_CARD);
            card.updateBillingCycle(15, 14, 25, null, null);

            LedgerBillingCycle.Cycle cycle =
                    LedgerBillingCycle.covering(card, LocalDate.parse("2026-08-14"));

            assertThat(cycle.start()).isEqualTo(LocalDate.parse("2026-07-15"));
        }

        @Test
        @DisplayName("다음 사이클은 마감 다음 날부터다 — 하루도 비지 않는다")
        void nextCycleStartsRightAfter() {
            LedgerAsset card = monthlyCard();
            LedgerBillingCycle.Cycle august =
                    LedgerBillingCycle.covering(card, LocalDate.parse("2026-08-15"));

            LedgerBillingCycle.Cycle september = LedgerBillingCycle.next(card, august);

            assertThat(september.start()).isEqualTo(LocalDate.parse("2026-09-01"));
            assertThat(september.end()).isEqualTo(LocalDate.parse("2026-09-30"));
        }
    }
}
