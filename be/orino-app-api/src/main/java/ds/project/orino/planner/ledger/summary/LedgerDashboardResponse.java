package ds.project.orino.planner.ledger.summary;

import java.time.LocalDate;

/**
 * 대시보드. <b>v1은 껍데기다 — 알고 만든다</b>([D-7](Ledger-Open-Items)).
 *
 * <p>이 모듈에서 가장 중요한 블록인 2축 요약(`LDG-053`)·미납 경고(`LDG-046`)·다가오는 결제는
 * 전부 v1.5다. 예정 없이는 그릴 수 없고, 예정은 정기 항목·청구서에 딸려 있다.
 *
 * <p>그래서 <b>그 자리를 비워 두지 않고 아예 내리지 않는다.</b> `cashflow`·`upcoming`·`overdue`
 * 필드가 여기 없는 것은 누락이 아니라 결정이다 — 빈 카드가 있으면 고장난 것처럼 보인다.
 */
public record LedgerDashboardResponse(
        Spending spending,
        Income income,
        Todo todo,
        Period period
) {

    /**
     * 이번 달 소비. v1은 <b>이미 쓴 돈</b> 하나다.
     *
     * <p>「앞으로 쓸 돈」과 그 둘을 합친 예상액은 예정이 있어야 말이 된다(v1.5).
     *
     * @param spent 카드 대금 납부는 들어가지 않는다 — 그건 이체다(확정 명세 §3-2)
     */
    public record Spending(long spent) {
    }

    /** 이번 달 수입. */
    public record Income(long amount) {
    }

    /**
     * 정리할 것.
     *
     * @param uncategorized 미분류 건수. 목표는 월말 기준 5% 미만이다(확정 명세 §17)
     */
    public record Todo(long uncategorized) {
    }

    public record Period(LocalDate start, LocalDate end, int monthStartDay) {
    }
}
