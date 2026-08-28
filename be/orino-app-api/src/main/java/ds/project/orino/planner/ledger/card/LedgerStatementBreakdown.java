package ds.project.orino.planner.ledger.card;

/**
 * 청구액 산식(확정 명세 §7.4).
 *
 * <pre>
 * 청구액 = 사이클 내 사용 건 합계
 *        + 할부 회차분
 *        + 이월 잔액
 *        + 이자·수수료
 *        + 차액 조정
 *        − 환불
 *        − 청구 할인
 * </pre>
 *
 * <p><b>저장하지 않고 조회할 때마다 계산한다.</b> 그리고 합계만이 아니라 <b>항목을 그대로
 * 내려준다</b> — 화면이 이 줄들을 보여줘야 사람이 「왜 이 금액이지」에 스스로 답할 수 있다.
 * 숫자 하나만 주면 카드사 앱과 다를 때 어디가 다른지 알 방법이 없다.
 *
 * <p>여기 <b>이월이 들어 있다는 것</b>과, 그럼에도 <b>이월이 지출 합계에는 들어가지 않는다는
 * 것</b>이 함께 성립해야 한다(§7.5). 청구액은 「이번에 나갈 돈」이고 지출은 「쓴 돈」이라
 * 서로 다른 질문이다 — 이월을 지출로 세면 같은 돈을 두 번 센다.
 *
 * @param usage       사이클 내 사용 건 합계(환불 제외)
 * @param installment 그 달에 잡히는 할부 회차분
 * @param carriedOver 지난 청구서에서 넘어온 잔액. <b>지출이 아니다</b>
 * @param interestFee 리볼빙 수수료·연체 이자. <b>이것만</b> 새 지출이 된다
 * @param adjustment  실제 청구액과의 차액(±)
 * @param refund      상쇄된 금액
 * @param discount    청구 할인
 * @param billed      위 항목을 다 반영한 청구액
 * @param paid        지금까지 낸 금액
 * @param remaining   아직 낼 금액. 부분 납부의 잔액이 여기다
 */
public record LedgerStatementBreakdown(
        long usage,
        long installment,
        long carriedOver,
        long interestFee,
        long adjustment,
        long refund,
        long discount,
        long billed,
        long paid,
        long remaining
) {

    public static LedgerStatementBreakdown of(long usage, long installment, long carriedOver,
                                              long interestFee, long adjustment, long refund,
                                              long discount, long paid) {
        long billed = usage + installment + carriedOver + interestFee + adjustment
                - refund - discount;
        // 낸 돈이 청구액을 넘어도 남은 금액은 음수가 되지 않는다 — 초과 납부는 다음 달의
        // 이야기이고, 여기서 음수로 보이면 「돌려받을 돈」처럼 읽힌다.
        long remaining = Math.max(billed - paid, 0);
        return new LedgerStatementBreakdown(usage, installment, carriedOver, interestFee,
                adjustment, refund, discount, billed, paid, remaining);
    }
}
