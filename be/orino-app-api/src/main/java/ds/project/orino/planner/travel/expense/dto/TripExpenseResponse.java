package ds.project.orino.planner.travel.expense.dto;

import ds.project.orino.domain.planner.travel.entity.TripStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 경비 화면 한 벌(API §11). <b>읽기 뷰</b>다 — 데이터의 출처는 가계부 원장이고, 여행은 그것을
 * 여행의 문법(출발 전 · N일차 · 도시)으로 다시 묶어 내릴 뿐이다.
 *
 * @param todayDayNumber 오늘이 며칠차인지. 여행 중일 때만 채워진다
 * @param budget         <b>예산을 안 정했으면 통째로 {@code null}</b>이다. {@code amount: 0}을
 *                       내리면 화면이 「0원 중 41.2만」을 그린다(§5.3)
 */
public record TripExpenseResponse(
        Long tripId,
        TripStatus status,
        Integer todayDayNumber,
        BudgetView budget,
        TotalsView totals,
        /** 카테고리가 비어 있는 건수. 「정리할 내역 N건」이 이 값이다. */
        int unsortedCount,
        List<ExpenseGroup> groups
) {

    /**
     * 예산과 그 위의 파생값.
     *
     * @param scheduled      예정 지출. 게이지 2층이 이 값이다
     * @param daysLeft       남은 날짜. 여행이 끝났으면 {@code null}
     * @param dailyAllowance 남은 돈 ÷ 남은 날짜. <b>여행이 끝나면 {@code null}</b>이 되고
     *                       그 자리를 {@link TotalsView#dailyAverage}가 받는다 — 둘이 동시에
     *                       차지 않는다
     */
    public record BudgetView(
            long amount,
            long spent,
            long scheduled,
            long remaining,
            Integer daysLeft,
            Long dailyAllowance
    ) {
    }

    /**
     * 예산과 무관한 총계. <b>예산을 안 정했어도 온다</b> — 「얼마 썼나」는 예산 없이도 답이 있다.
     *
     * @param dailyAverage 총액 ÷ 총 일수. <b>여행이 끝났을 때만</b> 채워진다
     */
    public record TotalsView(
            long spent,
            long scheduled,
            int days,
            Long dailyAverage
    ) {
    }

    /**
     * 날짜 묶음 하나.
     *
     * @param key       {@code BEFORE} · {@code DAY-N} · {@code AFTER}
     * @param dayNumber 기간 안의 날짜에만 있다
     * @param cityName  그 날짜의 기준 도시. <b>도시가 바뀌는 날은 도착 도시 하나로 센다</b>(§4.4)
     */
    public record ExpenseGroup(
            String key,
            String label,
            Integer dayNumber,
            LocalDate date,
            String cityName,
            long sum,
            List<ExpenseRow> rows
    ) {
    }

    /**
     * 지출 한 줄.
     *
     * <p><b>편집용 필드를 싣지 않는다.</b> 행을 누르면 가계부 지출 상세로 간다 —
     * 여행 안에 편집 화면을 두 벌 만들지 않는다(§6).
     *
     * @param amount 원화 환산액. <b>집계는 전부 이 값만 읽는다</b>
     * @param fx     외화 근거. 표시용이고 합계는 여기를 보지 않는다
     */
    public record ExpenseRow(
            Long transactionId,
            String title,
            long amount,
            FxView fx,
            String status,
            boolean uncategorized,
            LocalDate occurredOn
    ) {
    }

    /** 쓴 날의 환율로 굳은 값. 조회할 때 다시 계산하지 않는다(§4.3). */
    public record FxView(String currency, BigDecimal amount, BigDecimal rate) {
    }
}
