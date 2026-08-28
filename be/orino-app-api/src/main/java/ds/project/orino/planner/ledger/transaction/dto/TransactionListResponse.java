package ds.project.orino.planner.ledger.transaction.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 내역. <b>확정과 예정이 같은 타임라인 위에 있다</b> — 각 줄의 {@code status}가 둘을 가른다.
 *
 * <p>기준선({@code todayLine})을 서버가 정한다. 화면마다 「오늘」을 다시 계산하면 시간대가
 * 갈리는 순간 줄이 엉뚱한 곳에 그어진다.
 *
 * <p>예정을 별도 배열로 내려보내지 않는다. v1의 예정은 <b>직접 예약한 거래</b>뿐이고, 정기
 * 회차·카드 결제 예정·할부 잔여를 합친 4출처 UNION은 v1.5(#1264)에서 이 응답에 더해진다 —
 * 지금 자리만 만들어 두면 그 배열이 무엇을 담는지 두 번 정하게 된다.
 */
public record TransactionListResponse(
        LocalDate todayLine,
        MonthTotals monthTotals,
        List<DateGroup> groups
) {

    /**
     * 기간 합계.
     *
     * <p><b>이체는 지출에도 수입에도 들어가지 않는다</b>(확정 명세 §3-2). 따로 세어 보여줄 뿐이다 —
     * 카드 대금 납부가 지출로 새는 유일한 구멍을 이 규칙이 막는다.
     *
     * <p>환불은 「수입이 늘었다」가 아니라 「지출이 줄었다」로 반영된다.
     */
    public record MonthTotals(
            long income,
            long expense,
            long transfer,
            long scheduledExpense,
            long scheduledIncome,
            int scheduledCount
    ) {
    }

    /** 하루치 묶음. 날짜 헤더에 그날의 수입·지출 합계가 함께 붙는다. */
    public record DateGroup(
            LocalDate date,
            long income,
            long expense,
            List<TransactionView> items
    ) {
    }
}
