package ds.project.orino.domain.planner.ledger.entity;

/**
 * 돈이 움직이는 방향. 거래의 {@code type}과 카테고리의 {@code flow}가 <b>같은 값 공간</b>을 쓴다.
 *
 * <p>둘을 다른 enum으로 두면 "카테고리 흐름이 거래 유형과 다르다"(LDG-ERR-005)를 문자열
 * 비교로 확인하게 되고, 그 순간 규칙이 컴파일러의 손을 떠난다.
 *
 * <p>{@link #TRANSFER}는 <b>지출·수입 합계에 절대 잡히지 않는다</b>(확정 명세 §3-2).
 * 카드 대금 납부가 지출로 새는 유일한 구멍이 여기다.
 */
public enum LedgerFlow {
    EXPENSE,
    INCOME,
    TRANSFER
}
