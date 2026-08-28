package ds.project.orino.planner.ledger.asset.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 자산 상세 — 잔액 · 추이 · 카테고리 분포.
 *
 * @param range  추이의 눈금. {@code day}는 최근 30일, {@code month}는 최근 12개월,
 *               {@code year}는 최근 5년이다
 * @param trend  구간 끝 시점의 잔액. <b>원장을 처음부터 따라가며 만든 값</b>이라
 *               어느 시점을 찍어도 그때의 원장과 일치한다
 */
public record AssetDetailResponse(
        AssetView asset,
        Range range,
        List<TrendPoint> trend,
        List<CategoryShare> categoryShare
) {

    public enum Range {
        DAY,
        MONTH,
        YEAR
    }

    /**
     * 추이 한 점.
     *
     * @param balance 그 구간이 끝난 시점의 잔액(신용카드면 미결제 사용액)
     */
    public record TrendPoint(
            LocalDate date,
            long balance,
            long income,
            long expense
    ) {
    }

    /**
     * 카테고리 분포 한 칸.
     *
     * @param categoryId {@code null}이면 <b>미분류</b>다. 빼지 않는다 —
     *                   안 보이면 정리하지 않는다
     */
    public record CategoryShare(
            Long categoryId,
            String categoryName,
            long amount,
            long count
    ) {
    }
}
