package ds.project.orino.planner.review.sm2;

import ds.project.orino.domain.planner.review.entity.Rating;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Sm2Calculator {

    public static final BigDecimal MIN_EASE = new BigDecimal("1.30");

    private Sm2Calculator() {
    }

    public record Result(int intervalDays, BigDecimal easeFactor) {
    }

    public static Result next(int newSequence, int prevIntervalDays, BigDecimal prevEase, Rating rating) {
        int q = qScore(rating);

        if (q < 3) {
            BigDecimal newEase = clampEase(prevEase.subtract(new BigDecimal("0.20")));
            return new Result(1, newEase);
        }

        int intervalDays;
        if (newSequence == 1) {
            intervalDays = 1;
        } else if (newSequence == 2) {
            intervalDays = 6;
        } else {
            intervalDays = (int) Math.round(prevIntervalDays * prevEase.doubleValue());
        }

        double diff = 5 - q;
        double delta = 0.1 - diff * (0.08 + diff * 0.02);
        BigDecimal newEase = clampEase(
                prevEase.add(BigDecimal.valueOf(delta).setScale(2, RoundingMode.HALF_UP)));

        return new Result(intervalDays, newEase);
    }

    private static int qScore(Rating rating) {
        return switch (rating) {
            case AGAIN -> 0;
            case HARD -> 3;
            case GOOD -> 4;
            case EASY -> 5;
        };
    }

    private static BigDecimal clampEase(BigDecimal value) {
        return value.compareTo(MIN_EASE) < 0 ? MIN_EASE : value.setScale(2, RoundingMode.HALF_UP);
    }
}
