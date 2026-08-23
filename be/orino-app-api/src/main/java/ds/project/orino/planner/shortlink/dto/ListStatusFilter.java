package ds.project.orino.planner.shortlink.dto;

/**
 * 목록 상태 칩. {@code INACTIVE}는 <b>꺼짐과 만료를 함께</b> 담는다 — 화면의 칩이 셋뿐이고,
 * 사용자에게 둘은 "지금 안 열리는 링크"라는 한 덩어리다.
 */
public enum ListStatusFilter {
    ALL,
    ACTIVE,
    INACTIVE
}
