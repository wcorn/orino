package ds.project.orino.planner.travel.expense.dto;

import ds.project.orino.domain.planner.travel.entity.TripExpenseCategory;
import ds.project.orino.domain.planner.travel.entity.TripStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 경비 화면 한 벌(경비 독립 §6). 여행 전용 장부({@code trip_expense})를 여행의 문법
 * (출발 전 · N일차 · 도시)으로 묶어 내린다.
 *
 * <p>모양은 가계부 위의 읽기 뷰였을 때와 <b>거의 같다</b> — 그룹 규칙, 빈 날짜 {@code sum: 0},
 * 예산이 없으면 통째로 {@code null}, {@code dailyAllowance}의 하한 0까지 그대로다. 화면
 * 레이아웃을 바꾸는 변경이 아니기 때문이다.
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
        /** 분류가 비어 있는 건수. 「정리할 내역 N건」이 이 값이다. */
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
     * 지출 한 줄. <b>편집 시트가 이 값으로 열린다.</b>
     *
     * <p>예전에는 편집용 필드를 싣지 않았다 — 행을 누르면 가계부 지출 상세로 나갔기
     * 때문이다(D-35). 갈 곳이 없어졌고 편집 시트가 여행 안에 있으므로, 화면이 시트를 열자고
     * 행마다 다시 물어볼 이유가 없다. {@code category}·{@code paymentMethod}가 그래서 있다.
     *
     * <p>POST·PATCH의 응답도 이 모양이다. 저장하고 나면 화면이 그 줄만 갈아 끼우면 된다.
     *
     * @param amount  원화 환산액. <b>집계는 전부 이 값만 읽는다</b>. 환율을 못 가져온 외화
     *                건은 0이고, 그 사실은 {@code fx.rate}가 {@code null}인 것으로 드러난다
     * @param fx      외화 근거. 표시용이고 합계는 여기를 보지 않는다
     * @param overdue 날짜가 지났는데 아직 예정이다. 화면이 「확정」 버튼을 건다(§4.3)
     */
    public record ExpenseRow(
            Long expenseId,
            String title,
            long amount,
            FxView fx,
            String status,
            TripExpenseCategory category,
            String paymentMethod,
            boolean uncategorized,
            boolean overdue,
            LocalDate occurredOn
    ) {
    }

    /**
     * 쓴 날의 환율로 굳은 값. 조회할 때 다시 계산하지 않는다(§4.3).
     *
     * <p>{@code rate}가 {@code null}이면 ECB에 닿지 못한 채 저장된 건이다. 에러로 만들지
     * 않는 대신 화면이 그 자리에 직접 입력 칸을 연다 — 환율 때문에 기록을 막지 않는다.
     */
    public record FxView(String currency, BigDecimal amount, BigDecimal rate) {
    }
}
