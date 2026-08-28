package ds.project.orino.domain.planner.ledger.entity;

/**
 * 통계를 보는 기준. 소비(쓴 날)와 청구(빠져나가는 달)는 할부에서 가장 크게 벌어진다.
 *
 * <p>관점 전환 화면은 v2다. v1은 이 값을 <b>보관만</b> 한다 — 설정이 먼저 서 있어야
 * 나중에 화면이 붙을 때 기본값을 새로 정하지 않는다.
 */
public enum LedgerPerspective {
    SPEND,
    BILLING
}
