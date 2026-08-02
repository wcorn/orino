package ds.project.orino.planner.review.sm2;

import ds.project.orino.domain.planner.review.entity.Rating;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 다음 복습 간격·ease 계산 — <b>Anki의 SM-2 스케줄러</b>를 그대로 옮겼다
 * ({@code rslib/src/scheduler/states/review.rs}).
 *
 * <p>순정 SM-2는 q≥3(Hard/Good/Easy)에서 간격을 {@code 직전간격 × 직전ease}로만 정해 <b>세 등급이
 * 모두 같은 간격</b>이 나온다. rating은 ease만 바꾸고 그 ease는 다음 회차에나 쓰이므로 효과가 한 회차
 * 늦고, 당장은 어느 버튼을 눌러도 동일해 채점이 무의미해진다(#1001). Anki가 SM-2를 그대로 쓰지 않는
 * 이유가 이것이다.
 *
 * <pre>
 * hard = max(round(직전간격 × 1.2),                    직전간격 + 1)
 * good = max(round((직전간격 + 밀린일/2) × ease),        hard + 1)
 * easy = max(round((직전간격 + 밀린일) × ease × 1.3),    good + 1)
 * </pre>
 *
 * <p>세 가지가 핵심이다.
 * <ul>
 *   <li><b>ease는 갱신 전 값</b>을 간격에 쓴다. 평가로 바뀐 ease는 다음 회차부터 적용된다
 *       (Anki {@code answer_easy}는 반환 상태의 ease만 올리고 간격엔 {@code self.ease_factor}를 쓴다)</li>
 *   <li><b>하한</b>으로 {@code hard < good < easy} 순서를 강제한다. 배수만으로는 반올림 탓에
 *       뒤집히거나 같아질 수 있다</li>
 *   <li><b>밀린 일수</b>(days_late)를 보너스로 얹는다 — 늦게 봤는데도 기억했다면 그만큼 더 벌린다.
 *       Good은 절반만, Easy는 전부 반영한다</li>
 * </ul>
 *
 * <p>Anki의 학습 단계(learning steps)·졸업 간격은 두지 않는다. orino는 카드 생성 시 1일 뒤 첫 복습을
 * 잡는 것이 졸업 간격 역할을 하고, 그 뒤로는 위 수식만 탄다(고정 단계 없음).
 */
public final class Sm2Calculator {

    /** Anki {@code INITIAL_EASE_FACTOR}. */
    public static final BigDecimal INITIAL_EASE = new BigDecimal("2.50");
    /** Anki {@code MINIMUM_EASE_FACTOR} — ease는 이 아래로 떨어지지 않는다. */
    public static final BigDecimal MIN_EASE = new BigDecimal("1.30");

    /** Anki {@code EASE_FACTOR_AGAIN_DELTA}. */
    private static final BigDecimal AGAIN_EASE_DELTA = new BigDecimal("-0.20");
    /** Anki {@code EASE_FACTOR_HARD_DELTA}. */
    private static final BigDecimal HARD_EASE_DELTA = new BigDecimal("-0.15");
    /** Anki {@code EASE_FACTOR_EASY_DELTA}. */
    private static final BigDecimal EASY_EASE_DELTA = new BigDecimal("0.15");

    /** Anki 덱 옵션 {@code hard_multiplier} 기본값. */
    private static final BigDecimal HARD_MULTIPLIER = new BigDecimal("1.2");
    /** Anki 덱 옵션 {@code easy_multiplier}(Easy Bonus) 기본값. */
    private static final BigDecimal EASY_MULTIPLIER = new BigDecimal("1.3");
    private static final BigDecimal HALF = new BigDecimal("0.5");

    /** AGAIN이면 간격을 처음으로 되돌린다(due는 당일 10분 뒤 — 재학습 단계). */
    private static final int RELEARN_INTERVAL_DAYS = 1;

    private Sm2Calculator() {
    }

    public record Result(int intervalDays, BigDecimal easeFactor) {
    }

    /**
     * @param prevIntervalDays 직전 복습에서 잡았던 간격(일)
     * @param prevEase         직전 ease — <b>간격 계산에 쓰이는 값</b>이다(평가 반영 전)
     * @param daysLate         예정일보다 며칠 늦게 봤는지(0 이상). 늦은 만큼 보너스가 붙는다
     */
    public static Result next(int prevIntervalDays, BigDecimal prevEase, int daysLate, Rating rating) {
        if (rating == Rating.AGAIN) {
            return new Result(RELEARN_INTERVAL_DAYS, clampEase(prevEase.add(AGAIN_EASE_DELTA)));
        }
        return new Result(
                intervalDays(prevIntervalDays, prevEase, Math.max(0, daysLate), rating),
                clampEase(prevEase.add(easeDelta(rating))));
    }

    /**
     * 옛 규칙(순정 SM-2)의 간격. 이미 잡힌 일정을 새 규칙으로 재계산할 때 "아직 옛 규칙이 계산한
     * 자리에 그대로 있는 행"만 골라내는 데 쓴다(#1001 백필). 새 일정 계산에는 쓰지 않는다.
     */
    public static int legacyIntervalDays(int newSequence, int prevIntervalDays, BigDecimal prevEase, Rating rating) {
        if (rating == Rating.AGAIN) {
            return 1;
        }
        return switch (newSequence) {
            case 1 -> 1;
            case 2 -> 6;
            default -> (int) Math.round(prevIntervalDays * prevEase.doubleValue());
        };
    }

    /** 옛 규칙(순정 SM-2)의 ease 갱신폭. 백필 판별용 — {@link #legacyIntervalDays}와 같은 용도다. */
    public static BigDecimal legacyEaseFactor(BigDecimal prevEase, Rating rating) {
        // SM-2: ease' = ease + (0.1 - (5-q)·(0.08 + (5-q)·0.02)) — q는 AGAIN 0 / HARD 3 / GOOD 4 / EASY 5
        BigDecimal delta = switch (rating) {
            case AGAIN -> new BigDecimal("-0.20");
            case HARD -> new BigDecimal("-0.14");
            case GOOD -> BigDecimal.ZERO;
            case EASY -> new BigDecimal("0.10");
        };
        return clampEase(prevEase.add(delta));
    }

    private static int intervalDays(int prevIntervalDays, BigDecimal prevEase, int daysLate, Rating rating) {
        BigDecimal prev = BigDecimal.valueOf(prevIntervalDays);
        BigDecimal late = BigDecimal.valueOf(daysLate);

        // hard는 ease를 타지 않는다 — 고정 배수라 어려운 카드가 ease와 무관하게 완만히 늘어난다.
        int hard = atLeast(prev.multiply(HARD_MULTIPLIER), prevIntervalDays + 1);
        if (rating == Rating.HARD) {
            return hard;
        }

        int good = atLeast(prev.add(late.multiply(HALF)).multiply(prevEase), hard + 1);
        if (rating == Rating.GOOD) {
            return good;
        }

        return atLeast(prev.add(late).multiply(prevEase).multiply(EASY_MULTIPLIER), good + 1);
    }

    private static BigDecimal easeDelta(Rating rating) {
        return switch (rating) {
            case AGAIN -> AGAIN_EASE_DELTA;
            case HARD -> HARD_EASE_DELTA;
            case GOOD -> BigDecimal.ZERO;
            case EASY -> EASY_EASE_DELTA;
        };
    }

    /** 반올림한 일수와 하한 중 큰 값. 하한이 {@code hard < good < easy} 순서를 보장한다. */
    private static int atLeast(BigDecimal days, int minimum) {
        return Math.max(minimum, days.setScale(0, RoundingMode.HALF_UP).intValue());
    }

    private static BigDecimal clampEase(BigDecimal value) {
        return value.compareTo(MIN_EASE) < 0 ? MIN_EASE : value.setScale(2, RoundingMode.HALF_UP);
    }
}
