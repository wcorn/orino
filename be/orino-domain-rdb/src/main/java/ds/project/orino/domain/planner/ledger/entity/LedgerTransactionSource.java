package ds.project.orino.domain.planner.ledger.entity;

/**
 * 이 거래가 어디서 왔는지. 사람이 적었는지 서비스가 적었는지를 나중에 갈라 볼 수 있어야 한다.
 *
 * <p>{@link #REFUND}는 {@code refundOfId}와 함께 다닌다 — 상쇄 거래는 원 거래를 지우지 않고
 * 반대 방향으로 한 줄을 더 쓰는 것이다(확정 명세 §4.3). 지우면 실적·청구서 정합성이 함께 무너진다.
 *
 * <p>{@link #CARD_PAYMENT}는 반드시 {@link LedgerFlow#TRANSFER}다 — 카드 대금이 지출로 새는
 * 유일한 구멍을 그 규칙이 막는다. 카드 결제 처리 자체는 v1.5(#1262)다.
 */
public enum LedgerTransactionSource {
    MANUAL,
    RECURRING,
    SCHEDULED_ONE_OFF,
    CARD_PAYMENT,
    INSTALLMENT,
    ADJUSTMENT,
    REFUND,
    IMPORT
}
