package ds.project.orino.planner.ledger.summary;

import ds.project.orino.planner.ledger.upcoming.LedgerUpcomingDtos;

import java.time.LocalDate;
import java.util.List;

/**
 * 대시보드. v1.5에서 <b>2축 요약</b>이 채워졌다(`LDG-053` · 확정 명세 §8.2).
 *
 * <p>v1에서는 {@code cashflow}·{@code upcoming}·{@code netWorth} 필드가 아예 없었다 —
 * 예정 없이는 그릴 수 없어서 빈 카드를 만드느니 내리지 않았다(D-7). 이제 그 자리가 생겼다.
 */
public record LedgerDashboardResponse(
        Spending spending,
        Cashflow cashflow,
        Income income,
        NetWorth netWorth,
        List<LedgerUpcomingDtos.UpcomingItem> upcoming,
        Todo todo,
        Period period
) {

    /**
     * <b>이번 달 소비</b> — 「이번 달 얼마 쓰게 되나」에 답한다.
     *
     * <p>카드 <b>사용</b>은 들어가고 카드 <b>대금</b>은 안 들어간다. 계좌 간 이체도 빠진다 —
     * 기준은 <b>소비 시점</b>이다. 예산과 연결되는 축이 이쪽이다.
     *
     * @param spent     이미 쓴 돈
     * @param scheduled 아직 안 썼지만 나갈 게 확정된 지출
     * @param estimate  둘의 합
     */
    public record Spending(long spent, long scheduled, long estimate) {
    }

    /**
     * <b>통장에서 나갈 돈</b> — 「얼마 빠지고 얼마 남나」에 답한다.
     *
     * <p>지출 + 이체 + <b>카드 대금</b>이고 기준은 <b>출금 시점</b>이다. 잔액과 연결되는 축이
     * 이쪽이다. {@link Spending}과 <b>한 객체로 합치지 않는다</b> — 다른 질문에 답한다.
     *
     * @param balance         지금 쓸 수 있는 돈(저축 제외)
     * @param remainingOutflow 남은 예정 출금
     * @param remainingInflow  남은 예정 입금
     * @param monthEndBalance  월말 예상 잔액
     * @param minBalance       중간에 가장 낮아지는 지점. 월말 숫자만으로는 안 보인다
     */
    public record Cashflow(long balance, long remainingOutflow, long remainingInflow,
                           long monthEndBalance, LedgerUpcomingDtos.MinBalance minBalance) {
    }

    /** 이번 달 수입. */
    public record Income(long amount) {
    }

    /** 순자산 — 부채(카드 미결제·할부 잔여)를 반영한다. */
    public record NetWorth(long totalAssets, long liabilities, long netWorth) {
    }

    /**
     * 정리할 것.
     *
     * @param uncategorized 미분류 건수. 목표는 월말 기준 5% 미만이다(확정 명세 §17)
     * @param overdue       미납 건수. <b>「무시」가 없으므로</b> 확정하거나 건너뛰어야 준다
     */
    public record Todo(long uncategorized, long overdue) {
    }

    public record Period(LocalDate start, LocalDate end, int monthStartDay) {
    }
}
