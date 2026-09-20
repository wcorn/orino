package ds.project.orino.domain.planner.travel.entity;

/**
 * 여행 지출의 분류. <b>여섯으로 고정이고 일곱 번째를 만들지 않는다</b>(경비 독립 §4.1).
 *
 * <p>가계부의 분류 표를 옮겨 오지 않은 자리다. 근거는 준비 분류를 넷으로
 * 고정한 것({@link PrepCategory})과 같다 — 분류가 늘면 「어디에 적을지 고민하는 시간」이 늘고,
 * 그만큼 안 적게 된다. 여행 지출은 종류가 실제로 좁다.
 *
 * <p>사용자 정의 분류를 허용하지 않는다. 허용하는 순간 관리 화면·순서·삭제 시 재배정이
 * 따라오고, 그게 여행 안에 가계부를 다시 짓는 첫 걸음이다.
 *
 * <p><b>NULL을 허용한다.</b> 금액만 적고 저장하는 길이 이 기능의 핵심이라 분류는 끝까지
 * 선택 사항이고, 비어 있는 건수는 화면이 「정리할 내역 N건」으로 잡는다.
 */
public enum TripExpenseCategory {
    FOOD,
    TRANSPORT,
    STAY,
    SIGHT,
    SHOPPING,
    ETC
}
