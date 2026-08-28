package ds.project.orino.domain.planner.ledger.entity;

/**
 * 금액이 고정인지 변동인지.
 *
 * <p>{@link #VARIABLE}(공과금)로 적힌 거래에는 {@code estimated} 표시가 붙는다 — 예상액으로
 * 적힌 것이므로 고지서가 오면 고쳐야 한다는 뜻이고, 화면이 그 사실을 알린다.
 */
public enum LedgerAmountType {

    FIXED,
    VARIABLE
}
