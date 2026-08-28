package ds.project.orino.domain.planner.ledger.entity;

/**
 * 사람이 회차에 한 일(데이터 모델 §4.4).
 *
 * <p><b>{@link #UNPAID}와 {@link #SKIP}을 절대 같은 값으로 합치지 않는다.</b> 건너뛴 회차는
 * 없던 일이 되어 예정에서 사라지지만, 미납은 <b>여전히 내야 할 돈</b>이라 예정에 남고 예상
 * 잔액에 계속 반영된다. 이 구분이 무너지면 미납 상시 경고가 무의미해진다(확정 명세 §6.4).
 *
 * <p><b>「무시」에 해당하는 값은 없다.</b> 의도적으로 넣지 않았다 — 확정하거나 건너뛰어야만
 * 사라진다. 눈에 거슬리는 게 목적이다.
 */
public enum LedgerOverrideAction {

    /** 이번 회차만 금액이 다르다. 규칙은 그대로다. */
    AMOUNT,

    /** 이번 회차만 없던 일로. 다음 회차는 정상 진행된다. */
    SKIP,

    /** 날짜를 옮겼다. 키는 여전히 원래 예정일이다. */
    MOVE,

    /** 결제 실패. 장부에서는 빠지되 예정에는 미납 표시로 남는다. */
    UNPAID,

    /**
     * 자동 기록을 되돌렸다. 장부에서 빠져도 <b>이 행은 남는다</b> — 몇 달째 되돌리고 있는지
     * 보여야 규칙을 정리할 마음이 생긴다.
     */
    REVERTED;

    /** 예정 목록에서 사라지는가. 미납은 여기 없다. */
    public boolean hidesFromUpcoming() {
        return this == SKIP || this == REVERTED;
    }

    /** 이미 적힌 거래를 장부에서 빼는가. */
    public boolean removesPosting() {
        return this == SKIP || this == REVERTED || this == UNPAID;
    }
}
