package ds.project.orino.domain.planner.ledger.entity;

/**
 * 카드 실적을 무엇으로 세는가(확정 명세 §7.6).
 *
 * <p><b>카드 속성이지 전역 설정이 아니다.</b> 카드사·상품마다 다르고, 전역으로 두면 카드 두 장을
 * 쓰는 순간 한쪽이 반드시 틀린다 — 그리고 틀린 쪽은 「실적을 채웠다고 믿었는데 안 채워진」
 * 형태로 드러난다.
 *
 * <p>할부에서 둘이 갈린다: 승인은 결제 시점의 <b>전액</b>, 청구는 그 달 <b>회차 금액</b>이다.
 */
public enum LedgerUsageGoalBasis {

    /** 승인 기준 — 긁은 날, 긁은 금액 전액. */
    APPROVAL,

    /** 청구 기준 — 그 달 청구서에 실린 금액. */
    BILLING
}
