package ds.project.orino.planner.review.sm2;

import ds.project.orino.domain.planner.review.entity.Rating;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * SM-2 (SuperMemo-2) 알고리즘으로 다음 복습 간격과 ease factor를 계산한다.
 * 모든 입력은 직전 복습(currentSequence)의 상태이며 다음 복습(newSequence = currentSequence + 1)의
 * 파라미터를 반환한다. 순수 함수로, side-effect가 없고 단일 호출당 동일한 입력은 동일한 출력을 낸다.
 */
public final class Sm2Calculator {

    public static final BigDecimal INITIAL_EASE = new BigDecimal("2.50");
    public static final BigDecimal MIN_EASE = new BigDecimal("1.30");

    private static final BigDecimal AGAIN_PENALTY = new BigDecimal("0.20");

    private Sm2Calculator() {
    }

    public record Result(int intervalDays, BigDecimal easeFactor) {
    }

    public static Result next(int currentSequence, int prevInterval, BigDecimal prevEase, Rating rating) {
        if (rating == Rating.AGAIN) {
            BigDecimal newEase = prevEase.subtract(AGAIN_PENALTY).max(MIN_EASE)
                    .setScale(2, RoundingMode.HALF_UP);
            return new Result(1, newEase);
        }

        int newSequence = currentSequence + 1;
        int newInterval = computeInterval(newSequence, prevInterval, prevEase);
        BigDecimal newEase = computeEase(prevEase, rating);
        return new Result(newInterval, newEase);
    }

    private static int computeInterval(int newSequence, int prevInterval, BigDecimal prevEase) {
        if (newSequence == 2) {
            return 6;
        }
        return (int) Math.round(prevInterval * prevEase.doubleValue());
    }

    private static BigDecimal computeEase(BigDecimal prevEase, Rating rating) {
        int q = rating.getQScore();
        double delta = 0.1 - (5 - q) * (0.08 + (5 - q) * 0.02);
        BigDecimal newEase = prevEase.add(BigDecimal.valueOf(delta))
                .setScale(2, RoundingMode.HALF_UP);
        return newEase.max(MIN_EASE);
    }
}
