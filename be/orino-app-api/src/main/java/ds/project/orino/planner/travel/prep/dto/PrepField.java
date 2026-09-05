package ds.project.orino.planner.travel.prep.dto;

/**
 * 수정 요청이 <b>비우겠다</b>고 지목할 수 있는 칸.
 *
 * <p>PATCH는 보낸 것만 바꾸므로 「안 보냄」과 「null로 바꿔 달라」를 값만으로는 구별할 수
 * 없다. 그 구별을 요청 안에 이름으로 적게 한다 — 편집 시트에서 기한 칸을 비우는 것과
 * 기한을 그대로 두는 것은 다른 일이다.
 */
public enum PrepField {

    QUANTITY,
    DUE_DAYS_BEFORE,
    URL,
    MEMO
}
