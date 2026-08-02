package ds.project.orino.planner.review.sm2;

import ds.project.orino.domain.planner.review.entity.Rating;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Anki({@code rslib/src/scheduler/states/review.rs})와 같은 값이 나오는지 검증한다.
 *
 * <pre>
 * hard = max(round(직전 × 1.2),                   직전 + 1)
 * good = max(round((직전 + 밀린일/2) × ease),       hard + 1)
 * easy = max(round((직전 + 밀린일) × ease × 1.3),   good + 1)
 * ease: AGAIN -0.20 / HARD -0.15 / GOOD 0 / EASY +0.15, 하한 1.30
 * </pre>
 */
class Sm2CalculatorTest {

    private static final BigDecimal INITIAL_EASE = new BigDecimal("2.50");
    private static final int ON_TIME = 0;

    private static int interval(int prevInterval, Rating rating) {
        return Sm2Calculator.next(prevInterval, INITIAL_EASE, ON_TIME, rating).intervalDays();
    }

    @Nested
    @DisplayName("ease 갱신 (Anki 상수)")
    class Ease {

        @ParameterizedTest(name = "{0} → ease {1}")
        @CsvSource({
                "AGAIN, 2.30",
                "HARD,  2.35",
                "GOOD,  2.50",
                "EASY,  2.65"
        })
        @DisplayName("AGAIN -0.20 / HARD -0.15 / GOOD 0 / EASY +0.15")
        void deltas(Rating rating, String expected) {
            assertThat(Sm2Calculator.next(6, INITIAL_EASE, ON_TIME, rating).easeFactor())
                    .isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("ease는 1.30 미만으로 떨어지지 않는다")
        void clamps_at_minimum() {
            BigDecimal low = new BigDecimal("1.40");

            assertThat(Sm2Calculator.next(30, low, ON_TIME, Rating.AGAIN).easeFactor())
                    .isEqualByComparingTo("1.30");
            assertThat(Sm2Calculator.next(30, low, ON_TIME, Rating.HARD).easeFactor())
                    .isEqualByComparingTo("1.30");
        }

        @Test
        @DisplayName("간격에는 갱신 전 ease를 쓴다 — 평가로 바뀐 ease는 다음 회차부터다")
        void interval_uses_ease_before_delta() {
            // EASY: (8 + 0) × 2.50 × 1.3 = 26. 갱신 후 ease(2.65)를 썼다면 27.6 → 28이 됐을 것이다.
            Sm2Calculator.Result r = Sm2Calculator.next(8, INITIAL_EASE, ON_TIME, Rating.EASY);

            assertThat(r.intervalDays()).isEqualTo(26);
            assertThat(r.easeFactor()).isEqualByComparingTo("2.65");
        }
    }

    @Nested
    @DisplayName("AGAIN 평가")
    class Again {

        @Test
        @DisplayName("간격을 1일로 되돌린다 (due는 당일 10분 뒤)")
        void resets_interval() {
            assertThat(interval(38, Rating.AGAIN)).isEqualTo(1);
            assertThat(interval(1, Rating.AGAIN)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("간격 계산 (Anki 수식)")
    class Intervals {

        @Test
        @DisplayName("HARD = 직전 × 1.2 (ease를 타지 않는다)")
        void hard_uses_fixed_multiplier() {
            assertThat(interval(8, Rating.HARD)).isEqualTo(10);   // round(9.6)
            assertThat(interval(20, Rating.HARD)).isEqualTo(24);  // round(24.0)
        }

        @Test
        @DisplayName("GOOD = 직전 × ease")
        void good_uses_ease() {
            assertThat(interval(8, Rating.GOOD)).isEqualTo(20);   // round(20.0)
            assertThat(interval(20, Rating.GOOD)).isEqualTo(50);  // round(50.0)
        }

        @Test
        @DisplayName("EASY = 직전 × ease × 1.3")
        void easy_adds_bonus() {
            assertThat(interval(8, Rating.EASY)).isEqualTo(26);   // round(26.0)
            assertThat(interval(20, Rating.EASY)).isEqualTo(65);  // round(65.0)
        }

        @Test
        @DisplayName("ease가 낮아도 HARD 간격은 직전보다 최소 하루는 길다")
        void hard_never_shrinks() {
            // round(1 × 1.2) = 1이지만 하한(직전+1)이 걸려 2일
            assertThat(interval(1, Rating.HARD)).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("등급 간 순서 (#1001 회귀)")
    class Ordering {

        @ParameterizedTest(name = "직전 {0}일 → hard < good < easy")
        @CsvSource({"1", "2", "3", "6", "8", "15", "20", "50", "95"})
        @DisplayName("하한이 hard < good < easy 순서를 보장한다")
        void strictly_increasing(int prevInterval) {
            int hard = interval(prevInterval, Rating.HARD);
            int good = interval(prevInterval, Rating.GOOD);
            int easy = interval(prevInterval, Rating.EASY);

            assertThat(hard).isGreaterThan(prevInterval);
            assertThat(good).isGreaterThan(hard);
            assertThat(easy).isGreaterThan(good);
        }

        @Test
        @DisplayName("ease가 하한(1.30)까지 떨어져도 순서가 뒤집히지 않는다")
        void holds_at_minimum_ease() {
            BigDecimal minEase = Sm2Calculator.MIN_EASE;
            // 배수만 보면 good(×1.30)이 hard(×1.20)에 붙어 반올림으로 같아질 수 있는 구간
            int hard = Sm2Calculator.next(10, minEase, ON_TIME, Rating.HARD).intervalDays();
            int good = Sm2Calculator.next(10, minEase, ON_TIME, Rating.GOOD).intervalDays();
            int easy = Sm2Calculator.next(10, minEase, ON_TIME, Rating.EASY).intervalDays();

            assertThat(hard).isEqualTo(12);
            assertThat(good).isEqualTo(13);
            assertThat(easy).isEqualTo(17);
        }
    }

    @Nested
    @DisplayName("밀린 복습 보너스 (days_late)")
    class DaysLate {

        @Test
        @DisplayName("GOOD은 밀린 일수의 절반만 얹는다")
        void good_adds_half() {
            // (10 + 8/2) × 2.5 = 35
            assertThat(Sm2Calculator.next(10, INITIAL_EASE, 8, Rating.GOOD).intervalDays()).isEqualTo(35);
        }

        @Test
        @DisplayName("EASY는 밀린 일수를 전부 얹는다")
        void easy_adds_all() {
            // (10 + 8) × 2.5 × 1.3 = 58.5 → 59
            assertThat(Sm2Calculator.next(10, INITIAL_EASE, 8, Rating.EASY).intervalDays()).isEqualTo(59);
        }

        @Test
        @DisplayName("HARD는 밀린 일수를 얹지 않는다")
        void hard_ignores_delay() {
            assertThat(Sm2Calculator.next(10, INITIAL_EASE, 8, Rating.HARD).intervalDays())
                    .isEqualTo(Sm2Calculator.next(10, INITIAL_EASE, ON_TIME, Rating.HARD).intervalDays());
        }

        @Test
        @DisplayName("일찍 본 복습(음수)은 보너스가 없다")
        void early_review_gets_no_bonus() {
            assertThat(Sm2Calculator.next(10, INITIAL_EASE, -5, Rating.GOOD).intervalDays())
                    .isEqualTo(Sm2Calculator.next(10, INITIAL_EASE, ON_TIME, Rating.GOOD).intervalDays());
        }
    }

    @Nested
    @DisplayName("Good만 눌러온 카드의 회차별 미리보기 (문서화)")
    class DocumentedTable {

        @Test
        @DisplayName("ease 2.50, 제때 복습 기준")
        void table() {
            assertThat(previewAt(1)).containsExactly(1, 2, 3, 4);
            assertThat(previewAt(3)).containsExactly(1, 4, 8, 10);
            assertThat(previewAt(8)).containsExactly(1, 10, 20, 26);
            assertThat(previewAt(20)).containsExactly(1, 24, 50, 65);
        }

        private Integer[] previewAt(int prevInterval) {
            return new Integer[]{
                    interval(prevInterval, Rating.AGAIN),
                    interval(prevInterval, Rating.HARD),
                    interval(prevInterval, Rating.GOOD),
                    interval(prevInterval, Rating.EASY)
            };
        }
    }

    @Nested
    @DisplayName("옛 규칙 (백필 판별용)")
    class Legacy {

        @Test
        @DisplayName("순정 SM-2 — 세 등급이 모두 같은 간격이었다")
        void intervals_were_identical() {
            assertThat(Sm2Calculator.legacyIntervalDays(2, 1, INITIAL_EASE, Rating.HARD)).isEqualTo(6);
            assertThat(Sm2Calculator.legacyIntervalDays(2, 1, INITIAL_EASE, Rating.GOOD)).isEqualTo(6);
            assertThat(Sm2Calculator.legacyIntervalDays(2, 1, INITIAL_EASE, Rating.EASY)).isEqualTo(6);

            assertThat(Sm2Calculator.legacyIntervalDays(3, 6, INITIAL_EASE, Rating.HARD)).isEqualTo(15);
            assertThat(Sm2Calculator.legacyIntervalDays(3, 6, INITIAL_EASE, Rating.EASY)).isEqualTo(15);
        }

        @ParameterizedTest(name = "{0} → 옛 ease {1}")
        @CsvSource({
                "AGAIN, 2.30",
                "HARD,  2.36",
                "GOOD,  2.50",
                "EASY,  2.60"
        })
        @DisplayName("옛 ease 갱신폭은 SM-2 공식(-0.20 / -0.14 / 0 / +0.10)이었다")
        void ease_deltas(Rating rating, String expected) {
            assertThat(Sm2Calculator.legacyEaseFactor(INITIAL_EASE, rating)).isEqualByComparingTo(expected);
        }
    }
}
