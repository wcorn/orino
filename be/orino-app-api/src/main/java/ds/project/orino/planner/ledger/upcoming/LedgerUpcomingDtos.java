package ds.project.orino.planner.ledger.upcoming;

import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 예정 목록(API 스펙 §4). */
public final class LedgerUpcomingDtos {

    private LedgerUpcomingDtos() {
    }

    /**
     * 예정의 네 출처(확정 명세 §8.1). <b>한 테이블에 있지 않다.</b>
     *
     * <p>직접 예약만 실체화돼 있고 나머지 셋은 규칙·청구서·할부에서 파생 계산한다. 종류를
     * 내려주는 이유는 화면이 배지를 다르게 그리기 때문만이 아니라, <b>같은 돈이 두 번 세어지지
     * 않았음</b>을 사람이 눈으로 확인할 수 있어야 하기 때문이다.
     */
    public enum Kind {
        RECURRING,
        ONE_OFF,
        CARD_PAYMENT,
        INSTALLMENT
    }

    /**
     * 예정 한 줄.
     *
     * @param date       실제로 빠지는 날. 영업일 보정·이동이 반영된 값이다
     * @param dday       음수면 이미 지났다는 뜻이고, 그건 미납이다
     * @param isTransfer 소비가 아니다 — 카드 대금·계좌 이체에 배지를 하나 더 단다(§8.3)
     * @param overdue    미납. 확정하거나 건너뛰어야만 사라진다(§6.4)
     * @param estimated  예상 금액. 실제로 나갈 때 고쳐야 한다
     */
    public record UpcomingItem(
            Kind kind,
            LocalDate date,
            long dday,
            String title,
            long amount,
            LedgerFlow flow,
            boolean isTransfer,
            boolean overdue,
            boolean estimated,
            /** 예산의 2단 게이지가 이 값으로 카테고리를 가른다. 이체·대금에는 없다. */
            Long categoryId,
            Long assetId,
            String assetName,
            Long transactionId,
            Long recurringId,
            LocalDate occurrenceDate,
            Long statementId,
            Long installmentId) {
    }

    /**
     * 잔액이 가장 낮아지는 지점과 그 이유.
     *
     * <p>「월말에 얼마 남나」보다 <b>「중간에 모자라지 않나」</b>가 먼저다. 25일에 청약이
     * 빠지고 나면 바닥인데 월말 숫자만 보면 괜찮아 보인다.
     */
    public record MinBalance(long amount, LocalDate date, String reason) {
    }

    /**
     * @param outflow 나갈 돈. <b>지출과 이체를 함께</b> 센다 — 저축으로 옮긴 돈도 이번 달에
     *                쓸 수 있는 돈에서는 빠진다
     * @param income  들어올 돈
     */
    public record UpcomingStats(
            long outflow,
            long income,
            long currentBalance,
            long expectedBalance,
            MinBalance minBalance,
            int count,
            Map<Kind, Integer> byKind) {
    }

    public record UpcomingResponse(
            LocalDate from,
            LocalDate to,
            int days,
            UpcomingStats stats,
            List<UpcomingItem> items) {
    }

    /**
     * 캘린더 하루(`LDG-021`).
     *
     * <p><b>과거는 확정, 미래는 예정</b>을 각각 담는다 — 한 칸에 합쳐 내리면 화면이 연하게
     * 그릴 수가 없고, 「이미 쓴 돈」과 「나갈 예정인 돈」이 같은 굵기로 보인다.
     */
    public record CalendarDay(
            LocalDate date,
            long income,
            long expense,
            long scheduledIncome,
            long scheduledExpense,
            long scheduledTransfer) {
    }

    public record CalendarResponse(
            String month,
            LocalDate todayLine,
            List<CalendarDay> days) {
    }
}
