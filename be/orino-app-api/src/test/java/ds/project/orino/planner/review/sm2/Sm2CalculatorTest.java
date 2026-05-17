package ds.project.orino.planner.review.sm2;

import ds.project.orino.domain.planner.review.entity.Rating;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class Sm2CalculatorTest {

    private static final BigDecimal INITIAL_EASE = new BigDecimal("2.50");

    @Nested
    @DisplayName("AGAIN 평가")
    class Again {

        @Test
        @DisplayName("interval=1, ease는 -0.20")
        void interval_and_ease() {
            Sm2Calculator.Result r = Sm2Calculator.next(2, 1, INITIAL_EASE, Rating.AGAIN);

            assertThat(r.intervalDays()).isEqualTo(1);
            assertThat(r.easeFactor()).isEqualByComparingTo("2.30");
        }

        @Test
        @DisplayName("ease는 1.30 미만으로 떨어지지 않는다 (클램프)")
        void ease_clamps_at_min() {
            Sm2Calculator.Result r = Sm2Calculator.next(5, 30,
                    new BigDecimal("1.40"), Rating.AGAIN);

            assertThat(r.easeFactor()).isEqualByComparingTo("1.30");
        }
    }

    @Nested
    @DisplayName("HARD 평가")
    class Hard {

        @Test
        @DisplayName("sequence 2 → interval=6, ease는 약간 감소 (-0.14)")
        void seq2() {
            Sm2Calculator.Result r = Sm2Calculator.next(2, 1, INITIAL_EASE, Rating.HARD);

            assertThat(r.intervalDays()).isEqualTo(6);
            assertThat(r.easeFactor()).isEqualByComparingTo("2.36");
        }

        @Test
        @DisplayName("sequence 3+ → round(prevInterval * prevEase)")
        void seq3_plus() {
            Sm2Calculator.Result r = Sm2Calculator.next(3, 6, INITIAL_EASE, Rating.HARD);

            assertThat(r.intervalDays()).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("GOOD 평가")
    class Good {

        @Test
        @DisplayName("ease 유지")
        void ease_unchanged() {
            Sm2Calculator.Result r = Sm2Calculator.next(3, 6, INITIAL_EASE, Rating.GOOD);

            assertThat(r.easeFactor()).isEqualByComparingTo("2.50");
        }

        @ParameterizedTest(name = "sequence={0}, prevInterval={1} → interval={2}")
        @CsvSource({
                "1, 0, 1",
                "2, 1, 6",
                "3, 6, 15",
                "4, 15, 38"
        })
        @DisplayName("sequence별 다음 interval")
        void intervals(int newSeq, int prevInterval, int expected) {
            Sm2Calculator.Result r = Sm2Calculator.next(newSeq, prevInterval, INITIAL_EASE, Rating.GOOD);

            assertThat(r.intervalDays()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("EASY 평가")
    class Easy {

        @Test
        @DisplayName("ease는 +0.10")
        void ease_increases() {
            Sm2Calculator.Result r = Sm2Calculator.next(3, 6, INITIAL_EASE, Rating.EASY);

            assertThat(r.easeFactor()).isEqualByComparingTo("2.60");
        }
    }
}
