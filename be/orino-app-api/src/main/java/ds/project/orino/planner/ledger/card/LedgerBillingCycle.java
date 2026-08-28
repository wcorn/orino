package ds.project.orino.planner.ledger.card;

import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerSettings;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 카드 사이클의 날짜 계산(확정 명세 §7.1).
 *
 * <p>예: 매월 1일~말일 사용 → <b>익월</b> 14일 결제. 시작일·마감일·결제일 셋을 카드마다 등록한다.
 *
 * <p>순수 계산이라 스프링을 타지 않는다 — <b>산식과 마찬가지로 이 부분이 틀리면 나머지가 전부
 * 틀리므로</b> 단위 테스트로 못박을 수 있어야 한다.
 */
public final class LedgerBillingCycle {

    private LedgerBillingCycle() {
    }

    /**
     * 사이클 하나.
     *
     * @param paymentDate <b>영업일 보정 전</b> 값이다. 보정은 공휴일을 아는 쪽에서 한다
     */
    public record Cycle(LocalDate start, LocalDate end, LocalDate paymentDate) {
    }

    /** {@code date}를 품는 사이클. */
    public static Cycle covering(LedgerAsset card, LocalDate date) {
        LocalDate start = startOnOrBefore(card, date);
        return from(card, start);
    }

    /** 시작일이 {@code start}인 사이클. */
    public static Cycle from(LedgerAsset card, LocalDate start) {
        LocalDate end = endOf(card, start);
        return new Cycle(start, end, paymentDateFor(card, end));
    }

    /** 다음 사이클. 스케줄러가 마감된 사이클 다음 것을 열 때 쓴다. */
    public static Cycle next(LedgerAsset card, Cycle cycle) {
        return from(card, cycle.end().plusDays(1));
    }

    private static LocalDate startOnOrBefore(LedgerAsset card, LocalDate date) {
        LocalDate candidate = dayIn(YearMonth.from(date), card.getCycleStartDay());
        return date.isBefore(candidate)
                ? dayIn(YearMonth.from(date).minusMonths(1), card.getCycleStartDay())
                : candidate;
    }

    /**
     * 마감일. 마감일이 시작일보다 <b>앞이면</b> 사이클이 다음 달로 넘어간다
     * (예: 15일 시작 · 14일 마감 = 15일~익월 14일).
     */
    private static LocalDate endOf(LedgerAsset card, LocalDate start) {
        YearMonth month = YearMonth.from(start);
        LocalDate sameMonth = dayIn(month, card.getCycleCloseDay());
        return sameMonth.isBefore(start)
                ? dayIn(month.plusMonths(1), card.getCycleCloseDay())
                : sameMonth;
    }

    /**
     * 결제일 — <b>마감한 달의 다음 달</b>이다.
     *
     * <p>「1일~말일 쓰고 익월 14일에 낸다」가 이 규칙이다. 마감과 결제 사이에 카드사가 청구서를
     * 만들 시간이 필요해서, 같은 달에 마감하고 결제하는 카드는 사실상 없다.
     */
    private static LocalDate paymentDateFor(LedgerAsset card, LocalDate end) {
        return dayIn(YearMonth.from(end).plusMonths(1), card.getPaymentDay());
    }

    /** 그 달에 실제로 존재하는 날. 말일(99)과 짧은 달을 함께 처리한다. */
    private static LocalDate dayIn(YearMonth month, int day) {
        if (day == LedgerSettings.LAST_DAY_OF_MONTH) {
            return month.atEndOfMonth();
        }
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }
}
