package ds.project.orino.planner.ledger.subscription;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 청약 인정 회차·금액 추정(덧붙인 명세 §3.2). 순수 계산이라 시계도 DB도 없다.
 *
 * <p><b>선납·연체 산식을 흉내 내지 않는다</b>(D-16). 선납인지는 은행에 어떻게 지정했느냐로
 * 갈려 원장에 없는 정보다. 짐작해 회차를 늘리면 청약홈과 다른 숫자를 확신 있게 보여주게 된다 —
 * 대신 상한을 넘은 달과 입금이 없는 달을 <b>드러낸다</b>.
 */
public final class LedgerSubscriptionEstimator {

    /** 이 달분까지는 월 10만 원만 인정됐다. 2024년 11월분부터 25만 원이다. */
    private static final YearMonth LOWER_CAP_THROUGH = YearMonth.of(2024, 10);
    private static final long LOWER_CAP = 100_000L;
    private static final long CAP = 250_000L;

    private LedgerSubscriptionEstimator() {
    }

    /** 그 달의 월 인정 상한. 제도가 바뀌면 여기만 고친다. */
    public static long monthlyCap(YearMonth month) {
        return month.isAfter(LOWER_CAP_THROUGH) ? CAP : LOWER_CAP;
    }

    /**
     * 기준값 + 기준 월 <b>다음 달부터</b> 이번 달까지의 입금으로 센다.
     *
     * <ul>
     *   <li>인정 회차 — 입금이 있는 달 수. <b>같은 달 여러 번 넣어도 1회</b>다</li>
     *   <li>인정 금액 — 달마다 {@code min(그 달 입금 합계, 월 상한)}의 합</li>
     * </ul>
     *
     * @param deposits     달별 확정 입금 합계. 없는 달은 0으로 본다
     * @param currentMonth 이번 달. <b>끝나지 않은 달에는 「입금 없음」을 붙이지 않는다</b> —
     *                     약정일이 아직 안 왔을 수 있다
     */
    public static Result estimate(Baseline baseline, Map<YearMonth, Long> deposits,
                                  YearMonth currentMonth) {
        int count = baseline.count();
        long amount = baseline.amount();
        List<Month> months = new ArrayList<>();
        for (YearMonth month = baseline.throughMonth().plusMonths(1);
                !month.isAfter(currentMonth); month = month.plusMonths(1)) {
            long deposited = deposits.getOrDefault(month, 0L);
            long cap = monthlyCap(month);
            long recognized = Math.min(deposited, cap);
            if (deposited > 0) {
                count++;
            }
            amount += recognized;
            months.add(new Month(month, deposited, recognized,
                    flagOf(deposited, cap, month.isBefore(currentMonth))));
        }
        return new Result(count, amount, months);
    }

    private static Flag flagOf(long deposited, long cap, boolean monthEnded) {
        if (deposited > cap) {
            return Flag.OVER_CAP;
        }
        if (deposited == 0 && monthEnded) {
            return Flag.NO_DEPOSIT;
        }
        return null;
    }

    /** 원장만으로는 판단할 수 없어 사람에게 보여줄 달. */
    public enum Flag {
        /** 상한을 넘게 넣었다 — 선납으로 지정했다면 청약홈 값과 어긋난다. */
        OVER_CAP,
        /** 끝난 달인데 입금이 없다 — 인정일이 늦춰질 수 있다. */
        NO_DEPOSIT
    }

    /** 청약홈에서 본 값. {@code throughMonth}는 몇 월분까지 인정됐는지다. */
    public record Baseline(int count, long amount, YearMonth throughMonth) {
    }

    /** 한 달. {@code flag}가 {@code null}이면 설명할 것이 없는 달이다. */
    public record Month(YearMonth month, long deposited, long recognized, Flag flag) {
    }

    public record Result(int count, long amount, List<Month> months) {
    }
}
