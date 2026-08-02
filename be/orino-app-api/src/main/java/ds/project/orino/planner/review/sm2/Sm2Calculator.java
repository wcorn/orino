package ds.project.orino.planner.review.sm2;

import ds.project.orino.domain.planner.review.entity.Rating;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 다음 복습 간격·ease 계산. SM-2를 뼈대로 하되 <b>Anki의 등급별 간격 배수</b>를 얹었다.
 *
 * <p>교과서 SM-2는 q≥3(Hard/Good/Easy)에서 간격을 {@code 직전간격 × 직전ease}로만 정해 <b>세 등급이
 * 모두 같은 간격</b>이 나온다. rating은 ease만 바꾸고 그 ease는 다음 회차에나 쓰이므로, 효과가 한 회차
 * 늦게 나타나고 당장은 어느 버튼을 눌러도 동일하다 — 채점이 무의미해진다(#1001). Anki가 SM-2를 그대로
 * 쓰지 않는 이유가 이것이라, 같은 처방을 따른다.
 *
 * <ul>
 *   <li>Hard는 ease와 무관하게 직전 간격의 {@value #HARD_FACTOR_TEXT}배로만 늘린다</li>
 *   <li>Easy는 Good 간격에 {@value #EASY_BONUS_TEXT}배 보너스를 더 곱한다</li>
 *   <li>새 ease를 <b>즉시</b> 간격에 반영한다(기존은 직전 ease를 써서 한 회차 늦었다)</li>
 * </ul>
 */
public final class Sm2Calculator {

    public static final BigDecimal MIN_EASE = new BigDecimal("1.30");

    private static final String HARD_FACTOR_TEXT = "1.2";
    private static final String EASY_BONUS_TEXT = "1.3";

    /** Hard: 직전 간격에 곱하는 배수(Anki 기본값). ease를 타지 않아 완만하게 늘어난다. */
    private static final BigDecimal HARD_FACTOR = new BigDecimal(HARD_FACTOR_TEXT);
    /** Easy: Good 간격에 추가로 곱하는 보너스(Anki 기본값). */
    private static final BigDecimal EASY_BONUS = new BigDecimal(EASY_BONUS_TEXT);
    /** 고정 단계(1·2회차)의 Hard 배수 — 곱할 직전 간격이 사실상 없어 Good 기준으로 나눈다. */
    private static final BigDecimal EARLY_HARD_FACTOR = new BigDecimal("0.5");

    private static final BigDecimal AGAIN_EASE_DELTA = new BigDecimal("-0.20");
    private static final BigDecimal HARD_EASE_DELTA = new BigDecimal("-0.15");
    private static final BigDecimal GOOD_EASE_DELTA = BigDecimal.ZERO;
    private static final BigDecimal EASY_EASE_DELTA = new BigDecimal("0.15");

    /** 고정 단계 간격(SM-2와 동일) — Good 기준값이고 Hard/Easy는 여기서 갈라진다. */
    private static final int FIRST_INTERVAL_DAYS = 1;
    private static final int SECOND_INTERVAL_DAYS = 6;

    private Sm2Calculator() {
    }

    public record Result(int intervalDays, BigDecimal easeFactor) {
    }

    public static Result next(int newSequence, int prevIntervalDays, BigDecimal prevEase, Rating rating) {
        if (rating == Rating.AGAIN) {
            // 실패는 처음부터 다시 — 간격을 되돌리고 ease를 깎는다(due 시각은 당일 10분 뒤).
            return new Result(1, clampEase(prevEase.add(AGAIN_EASE_DELTA)));
        }

        BigDecimal newEase = clampEase(prevEase.add(easeDelta(rating)));
        return new Result(intervalDays(newSequence, prevIntervalDays, newEase, rating), newEase);
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
            case 1 -> FIRST_INTERVAL_DAYS;
            case 2 -> SECOND_INTERVAL_DAYS;
            default -> (int) Math.round(prevIntervalDays * prevEase.doubleValue());
        };
    }

    private static int intervalDays(int newSequence, int prevIntervalDays, BigDecimal newEase, Rating rating) {
        int good = goodIntervalDays(newSequence, prevIntervalDays, newEase);
        return switch (rating) {
            // 1·2회차는 간격이 1일로 고정돼 있어 "직전 간격 × 1.2"가 의미를 못 갖는다.
            // 그 구간만 Good 기준으로 갈라 두고, 3회차부터 Anki와 같은 직전 간격 배수를 쓴다.
            case HARD -> newSequence >= 3
                    ? atLeastOneDay(BigDecimal.valueOf(prevIntervalDays).multiply(HARD_FACTOR))
                    : atLeastOneDay(BigDecimal.valueOf(good).multiply(EARLY_HARD_FACTOR));
            case EASY -> atLeastOneDay(BigDecimal.valueOf(good).multiply(EASY_BONUS));
            default -> good;
        };
    }

    /** Good 기준 간격. 1·2회차는 고정(1일·6일), 그 뒤는 직전 간격 × 새 ease. */
    private static int goodIntervalDays(int newSequence, int prevIntervalDays, BigDecimal newEase) {
        return switch (newSequence) {
            case 1 -> FIRST_INTERVAL_DAYS;
            case 2 -> SECOND_INTERVAL_DAYS;
            default -> atLeastOneDay(BigDecimal.valueOf(prevIntervalDays).multiply(newEase));
        };
    }

    private static BigDecimal easeDelta(Rating rating) {
        return switch (rating) {
            case AGAIN -> AGAIN_EASE_DELTA;
            case HARD -> HARD_EASE_DELTA;
            case GOOD -> GOOD_EASE_DELTA;
            case EASY -> EASY_EASE_DELTA;
        };
    }

    private static int atLeastOneDay(BigDecimal days) {
        return Math.max(1, days.setScale(0, RoundingMode.HALF_UP).intValue());
    }

    private static BigDecimal clampEase(BigDecimal value) {
        return value.compareTo(MIN_EASE) < 0 ? MIN_EASE : value.setScale(2, RoundingMode.HALF_UP);
    }
}
