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
        @DisplayName("고정 단계(2회차) → Good(6일)의 절반, ease -0.15")
        void seq2() {
            Sm2Calculator.Result r = Sm2Calculator.next(2, 1, INITIAL_EASE, Rating.HARD);

            assertThat(r.intervalDays()).isEqualTo(3);
            assertThat(r.easeFactor()).isEqualByComparingTo("2.35");
        }

        @Test
        @DisplayName("3회차부터 → 직전 간격 × 1.2 (ease를 타지 않는다)")
        void seq3_plus() {
            assertThat(Sm2Calculator.next(3, 6, INITIAL_EASE, Rating.HARD).intervalDays()).isEqualTo(7);
            assertThat(Sm2Calculator.next(4, 15, INITIAL_EASE, Rating.HARD).intervalDays()).isEqualTo(18);
        }

        @Test
        @DisplayName("ease가 낮아도 간격은 직전보다 줄지 않는다")
        void never_shrinks() {
            Sm2Calculator.Result r = Sm2Calculator.next(4, 10, new BigDecimal("1.30"), Rating.HARD);

            assertThat(r.intervalDays()).isEqualTo(12);
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
        @DisplayName("ease는 +0.15")
        void ease_increases() {
            Sm2Calculator.Result r = Sm2Calculator.next(3, 6, INITIAL_EASE, Rating.EASY);

            assertThat(r.easeFactor()).isEqualByComparingTo("2.65");
        }

        @Test
        @DisplayName("Good 간격 × 1.3 (새 ease를 즉시 반영)")
        void interval_bonus() {
            // 3회차: good = round(6 × 2.65) = 16 → 16 × 1.3 = 20.8 → 21
            assertThat(Sm2Calculator.next(3, 6, INITIAL_EASE, Rating.EASY).intervalDays()).isEqualTo(21);
            // 고정 단계(2회차): good = 6 → 6 × 1.3 = 7.8 → 8
            assertThat(Sm2Calculator.next(2, 1, INITIAL_EASE, Rating.EASY).intervalDays()).isEqualTo(8);
        }
    }

    @Nested
    @DisplayName("등급 간 간격 (#1001 회귀)")
    class RatingSpread {

        @ParameterizedTest(name = "sequence={0}, prevInterval={1} → hard<good<easy")
        @CsvSource({
                "2, 1",
                "3, 6",
                "4, 15",
                "5, 38"
        })
        @DisplayName("Hard < Good < Easy — 세 등급의 간격이 항상 갈린다")
        void hard_good_easy_differ(int newSeq, int prevInterval) {
            int hard = Sm2Calculator.next(newSeq, prevInterval, INITIAL_EASE, Rating.HARD).intervalDays();
            int good = Sm2Calculator.next(newSeq, prevInterval, INITIAL_EASE, Rating.GOOD).intervalDays();
            int easy = Sm2Calculator.next(newSeq, prevInterval, INITIAL_EASE, Rating.EASY).intervalDays();

            assertThat(hard).isLessThan(good);
            assertThat(good).isLessThan(easy);
        }

        @Test
        @DisplayName("ease 2.50 카드의 회차별 미리보기 (문서화)")
        void documented_table() {
            assertThat(previewAt(2, 1)).containsExactly(1, 3, 6, 8);
            assertThat(previewAt(3, 6)).containsExactly(1, 7, 15, 21);
            assertThat(previewAt(4, 15)).containsExactly(1, 18, 38, 52);
        }

        private Integer[] previewAt(int newSeq, int prevInterval) {
            return new Integer[]{
                    Sm2Calculator.next(newSeq, prevInterval, INITIAL_EASE, Rating.AGAIN).intervalDays(),
                    Sm2Calculator.next(newSeq, prevInterval, INITIAL_EASE, Rating.HARD).intervalDays(),
                    Sm2Calculator.next(newSeq, prevInterval, INITIAL_EASE, Rating.GOOD).intervalDays(),
                    Sm2Calculator.next(newSeq, prevInterval, INITIAL_EASE, Rating.EASY).intervalDays()
            };
        }
    }

    @Nested
    @DisplayName("옛 규칙 (백필 판별용)")
    class Legacy {

        @Test
        @DisplayName("순정 SM-2 — 세 등급이 모두 같은 간격이었다")
        void legacy_intervals_were_identical() {
            assertThat(Sm2Calculator.legacyIntervalDays(2, 1, INITIAL_EASE, Rating.HARD)).isEqualTo(6);
            assertThat(Sm2Calculator.legacyIntervalDays(2, 1, INITIAL_EASE, Rating.GOOD)).isEqualTo(6);
            assertThat(Sm2Calculator.legacyIntervalDays(2, 1, INITIAL_EASE, Rating.EASY)).isEqualTo(6);

            assertThat(Sm2Calculator.legacyIntervalDays(3, 6, INITIAL_EASE, Rating.HARD)).isEqualTo(15);
            assertThat(Sm2Calculator.legacyIntervalDays(3, 6, INITIAL_EASE, Rating.EASY)).isEqualTo(15);
        }

        @Test
        @DisplayName("GOOD은 새 규칙과 값이 같다 — 백필이 건드릴 게 없다")
        void good_unchanged_by_new_rule() {
            for (int seq = 2; seq <= 6; seq++) {
                int prev = seq == 2 ? 1 : 6;
                assertThat(Sm2Calculator.next(seq, prev, INITIAL_EASE, Rating.GOOD).intervalDays())
                        .isEqualTo(Sm2Calculator.legacyIntervalDays(seq, prev, INITIAL_EASE, Rating.GOOD));
            }
        }
    }
}
