package ds.project.orino.planner.ledger.upcoming;

import java.time.LocalDate;
import java.util.List;

/**
 * 일자별 예상 잔액(`LDG-054` · 확정 명세 §8.4).
 *
 * <pre>
 *   현재 계좌 잔액 − 남은 예정 지출·이체 + 남은 예정 수입 = 월말 예상 잔액
 * </pre>
 *
 * <p><b>월말 숫자 하나로는 못 잡는 것을 잡으려고 만든다.</b> 25일에 청약이 빠지고 나면 바닥인데
 * 월말에는 급여가 들어와 괜찮아 보이는 달이 있다 — 곡선은 그 사이를 보여준다.
 *
 * @param firstNegativeDate 잔액이 처음 마이너스가 되는 날. 없으면 {@code null}이다 —
 *                          0으로 두면 「오늘 이미 마이너스」로 읽힌다
 */
public record LedgerBalanceCurve(
        LocalDate from,
        LocalDate to,
        long currentBalance,
        List<Point> points,
        LedgerUpcomingDtos.MinBalance minBalance,
        LocalDate firstNegativeDate
) {

    /**
     * 곡선의 한 점.
     *
     * @param delta   그날 움직인 금액. 아무 일도 없는 날은 0이고, 그 날도 점을 찍는다 —
     *                빼면 화면이 날짜 간격을 스스로 메워야 하고 그때 선이 휜다
     * @param balance 그날이 끝났을 때의 잔액
     */
    public record Point(LocalDate date, long delta, long balance) {
    }
}
