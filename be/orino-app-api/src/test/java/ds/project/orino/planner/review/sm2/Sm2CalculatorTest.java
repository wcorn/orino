package ds.project.orino.planner.review.sm2;

import ds.project.orino.domain.planner.review.entity.Rating;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class Sm2CalculatorTest {

    private static final BigDecimal INITIAL_EASE = new BigDecimal("2.50");

    @Nested
    @DisplayName("AGAIN 평가 (q=0)")
    class Again {

        @Test
        @DisplayName("interval은 항상 1")
        void interval_alwaysOne() {
            Sm2Calculator.Result r1 = Sm2Calculator.next(1, 1, INITIAL_EASE, Rating.AGAIN);
            Sm2Calculator.Result r3 = Sm2Calculator.next(3, 15, new BigDecimal("2.50"), Rating.AGAIN);

            assertThat(r1.intervalDays()).isEqualTo(1);
            assertThat(r3.intervalDays()).isEqualTo(1);
        }

        @Test
        @DisplayName("ease는 prev_ease - 0.20")
        void ease_minus020() {
            Sm2Calculator.Result r = Sm2Calculator.next(1, 1, new BigDecimal("2.50"), Rating.AGAIN);

            assertThat(r.easeFactor()).isEqualByComparingTo(new BigDecimal("2.30"));
        }

        @Test
        @DisplayName("ease는 1.30 미만으로 떨어지지 않는다")
        void ease_clampedAt130() {
            Sm2Calculator.Result r = Sm2Calculator.next(5, 1, new BigDecimal("1.30"), Rating.AGAIN);

            assertThat(r.easeFactor()).isEqualByComparingTo(new BigDecimal("1.30"));
        }

        @Test
        @DisplayName("ease가 1.40에서 AGAIN 시 1.30으로 클램프")
        void ease_clampedFromLowEase() {
            Sm2Calculator.Result r = Sm2Calculator.next(2, 6, new BigDecimal("1.40"), Rating.AGAIN);

            assertThat(r.easeFactor()).isEqualByComparingTo(new BigDecimal("1.30"));
        }
    }

    @Nested
    @DisplayName("성공 평가 (HARD/GOOD/EASY)")
    class Success {

        @Test
        @DisplayName("첫 번째 복습 평가(seq=1) → 다음 복습(seq=2) interval = 6")
        void firstReview_nextIs6() {
            Sm2Calculator.Result good = Sm2Calculator.next(1, 1, INITIAL_EASE, Rating.GOOD);
            Sm2Calculator.Result hard = Sm2Calculator.next(1, 1, INITIAL_EASE, Rating.HARD);
            Sm2Calculator.Result easy = Sm2Calculator.next(1, 1, INITIAL_EASE, Rating.EASY);

            assertThat(good.intervalDays()).isEqualTo(6);
            assertThat(hard.intervalDays()).isEqualTo(6);
            assertThat(easy.intervalDays()).isEqualTo(6);
        }

        @Test
        @DisplayName("두 번째 복습 이후 → round(prev_interval × ease)")
        void laterReview_intervalIsMultiplied() {
            Sm2Calculator.Result r = Sm2Calculator.next(2, 6, new BigDecimal("2.50"), Rating.GOOD);

            assertThat(r.intervalDays()).isEqualTo(15);
        }

        @Test
        @DisplayName("HARD(q=3) → ease -= 0.14 (= 0.1 - 2*(0.08+2*0.02))")
        void hard_easeDecreases() {
            Sm2Calculator.Result r = Sm2Calculator.next(2, 6, new BigDecimal("2.50"), Rating.HARD);

            assertThat(r.easeFactor()).isEqualByComparingTo(new BigDecimal("2.36"));
        }

        @Test
        @DisplayName("GOOD(q=4) → ease 변화 없음 (= 0.1 - 1*(0.08+1*0.02) = 0)")
        void good_easeUnchanged() {
            Sm2Calculator.Result r = Sm2Calculator.next(2, 6, new BigDecimal("2.50"), Rating.GOOD);

            assertThat(r.easeFactor()).isEqualByComparingTo(new BigDecimal("2.50"));
        }

        @Test
        @DisplayName("EASY(q=5) → ease += 0.10")
        void easy_easeIncreases() {
            Sm2Calculator.Result r = Sm2Calculator.next(2, 6, new BigDecimal("2.50"), Rating.EASY);

            assertThat(r.easeFactor()).isEqualByComparingTo(new BigDecimal("2.60"));
        }

        @Test
        @DisplayName("ease 상한은 없다 (반복적 EASY로 증가 가능)")
        void easy_noUpperLimit() {
            Sm2Calculator.Result r = Sm2Calculator.next(2, 6, new BigDecimal("3.50"), Rating.EASY);

            assertThat(r.easeFactor()).isEqualByComparingTo(new BigDecimal("3.60"));
        }

        @Test
        @DisplayName("HARD 반복 시 ease는 1.30으로 클램프")
        void hard_easeClampedAt130() {
            Sm2Calculator.Result r = Sm2Calculator.next(2, 6, new BigDecimal("1.30"), Rating.HARD);

            assertThat(r.easeFactor()).isEqualByComparingTo(new BigDecimal("1.30"));
        }

        @Test
        @DisplayName("3차 복습 이후의 interval 계산 (round)")
        void thirdReview_rounded() {
            Sm2Calculator.Result r = Sm2Calculator.next(3, 15, new BigDecimal("2.50"), Rating.GOOD);

            assertThat(r.intervalDays()).isEqualTo(38);
        }
    }
}
