package ds.project.orino.planner.ledger.common;

import ds.project.orino.domain.planner.ledger.entity.LedgerSettings;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 「이번 달」의 경계. <b>월 시작일 설정이 반영된 구간</b>이다.
 *
 * <p>급여일 기준(25일)으로 살면 8월 20일은 아직 <b>7월 25일에 시작한 구간</b>이다. 요약·통계·
 * 예산이 모두 이 계산을 쓴다 — 각자 계산하면 화면마다 다른 달을 말하게 되고, 그건 이 모듈에서
 * 「원장이 틀어졌다」와 구분되지 않는다.
 *
 * <p>월 시작일은 <b>예산 기간에만</b> 쓴다(확정 명세 §9). 카드 사이클과 정기 주기는 여기에
 * 영향받지 않는다.
 */
public final class LedgerPeriods {

    private LedgerPeriods() {
    }

    /** 구간 하나. 양끝을 포함한다. */
    public record Period(LocalDate start, LocalDate end) {
    }

    /** {@code anchor}가 속한 구간. */
    public static Period containing(LocalDate anchor, int monthStartDay) {
        LocalDate start = startOnOrBefore(anchor, monthStartDay);
        return new Period(start, endOf(start, monthStartDay));
    }

    /**
     * 그 달에 <b>시작하는</b> 구간. {@code 2026-08}이면 8월의 시작일부터 다음 시작일 전날까지다.
     *
     * <p>월 시작일이 1이 아니면 구간이 두 달에 걸친다. 그때도 이름은 <b>시작한 달</b>이다 —
     * 「8월 급여로 사는 기간」이 사람이 그 구간을 부르는 이름이기 때문이다.
     */
    public static Period of(YearMonth month, int monthStartDay) {
        LocalDate start = startIn(month, monthStartDay);
        return new Period(start, endOf(start, monthStartDay));
    }

    private static LocalDate startOnOrBefore(LocalDate anchor, int monthStartDay) {
        LocalDate candidate = startIn(YearMonth.from(anchor), monthStartDay);
        // 시작일 전이면 아직 지난 구간에 있다.
        return anchor.isBefore(candidate)
                ? startIn(YearMonth.from(anchor).minusMonths(1), monthStartDay)
                : candidate;
    }

    private static LocalDate endOf(LocalDate start, int monthStartDay) {
        LocalDate next = startIn(YearMonth.from(start).plusMonths(1), monthStartDay);
        return next.minusDays(1);
    }

    /** 그 달에 실제로 존재하는 시작일. 말일(99)과 짧은 달을 함께 처리한다. */
    private static LocalDate startIn(YearMonth month, int monthStartDay) {
        if (monthStartDay == LedgerSettings.LAST_DAY_OF_MONTH) {
            return month.atEndOfMonth();
        }
        return month.atDay(Math.min(monthStartDay, month.lengthOfMonth()));
    }
}
