package ds.project.orino.planner.ledger.common;

import ds.project.orino.domain.planner.ledger.entity.LedgerSettings;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.UnaryOperator;

/**
 * 「이번 달」의 경계. <b>월 시작일 설정이 반영된 구간</b>이다.
 *
 * <p>급여일 기준(25일)으로 살면 8월 20일은 아직 <b>7월 25일에 시작한 구간</b>이다. 요약·통계·
 * 예산이 모두 이 계산을 쓴다 — 각자 계산하면 화면마다 다른 달을 말하게 되고, 그건 이 모듈에서
 * 「원장이 틀어졌다」와 구분되지 않는다.
 *
 * <p>월 시작일은 <b>예산 기간에만</b> 쓴다(확정 명세 §9). 카드 사이클과 정기 주기는 여기에
 * 영향받지 않는다.
 *
 * <p>주말 보정({@code adjust})은 <b>구간의 양끝에 함께</b> 적용된다. 시작만 당기면 앞 구간의
 * 끝과 겹치거나 하루가 빈다.
 */
public final class LedgerPeriods {

    private LedgerPeriods() {
    }

    /** 구간 하나. 양끝을 포함한다. */
    public record Period(LocalDate start, LocalDate end) {
    }

    /** {@code anchor}가 속한 구간. */
    public static Period containing(LocalDate anchor, int monthStartDay) {
        return containing(anchor, monthStartDay, UnaryOperator.identity());
    }

    /**
     * 보정을 입힌 구간. 25일 시작인데 그날이 토요일이면 급여는 24일(금)에 들어온다 —
     * 보정이 없으면 구간이 이틀 어긋난 채로 예산·요약·통계에 그대로 실린다.
     */
    public static Period containing(LocalDate anchor, int monthStartDay,
                                    UnaryOperator<LocalDate> adjust) {
        return of(monthOf(anchor, monthStartDay, adjust), monthStartDay, adjust);
    }

    /**
     * 그 달에 <b>시작하는</b> 구간. {@code 2026-08}이면 8월의 시작일부터 다음 시작일 전날까지다.
     *
     * <p>월 시작일이 1이 아니면 구간이 두 달에 걸친다. 그때도 이름은 <b>시작한 달</b>이다 —
     * 「8월 급여로 사는 기간」이 사람이 그 구간을 부르는 이름이기 때문이다.
     */
    public static Period of(YearMonth month, int monthStartDay) {
        return of(month, monthStartDay, UnaryOperator.identity());
    }

    public static Period of(YearMonth month, int monthStartDay,
                            UnaryOperator<LocalDate> adjust) {
        LocalDate start = startOf(month, monthStartDay, adjust);
        return new Period(start, startOf(month.plusMonths(1), monthStartDay, adjust).minusDays(1));
    }

    /**
     * 그 날짜를 품는 구간이 <b>시작한 달</b>.
     *
     * <p>보정 때문에 구간이 앞뒤로 끌려갈 수 있어 이웃 달까지 본다 — 시작일이 1일인데 그날이
     * 일요일이면 그 구간은 <b>지난달 금요일</b>에 시작한다. 후보 중 시작일이 {@code anchor}를
     * 넘지 않는 가장 늦은 달이 답이다.
     */
    private static YearMonth monthOf(LocalDate anchor, int monthStartDay,
                                     UnaryOperator<LocalDate> adjust) {
        YearMonth base = YearMonth.from(anchor);
        for (YearMonth candidate : new YearMonth[]{base.plusMonths(1), base, base.minusMonths(1)}) {
            if (!anchor.isBefore(startOf(candidate, monthStartDay, adjust))) {
                return candidate;
            }
        }
        return base.minusMonths(1);
    }

    private static LocalDate startOf(YearMonth month, int monthStartDay,
                                     UnaryOperator<LocalDate> adjust) {
        return adjust.apply(startIn(month, monthStartDay));
    }

    /** 그 달에 실제로 존재하는 시작일. 말일(99)과 짧은 달을 함께 처리한다. */
    private static LocalDate startIn(YearMonth month, int monthStartDay) {
        if (monthStartDay == LedgerSettings.LAST_DAY_OF_MONTH) {
            return month.atEndOfMonth();
        }
        return month.atDay(Math.min(monthStartDay, month.lengthOfMonth()));
    }
}
