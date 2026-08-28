package ds.project.orino.domain.planner.ledger.entity;

/**
 * 예정일이 주말·공휴일에 걸렸을 때 어디로 옮기는가(확정 명세 §6.2).
 *
 * <p>공휴일 자료는 플래너가 이미 갖고 있다 — 새 외부 API를 들이지 않는다(D-3).
 *
 * <p><b>보정 결과는 회차의 키가 아니다.</b> 키는 규칙이 계산한 원래 날짜이고, 보정은 「그날
 * 실제로 언제 빠지는가」만 바꾼다. 보정 결과를 키로 삼으면 공휴일 자료가 늦게 갱신될 때
 * 이미 적힌 회차가 다른 회차로 보여 두 번 적힌다.
 */
public enum LedgerBusinessDayPolicy {

    AS_IS,

    /** 앞 영업일로 당긴다. 자동이체 대부분이 이쪽이다. */
    PREV,

    NEXT
}
