package ds.project.orino.planner.shortlink.dto;

/**
 * 화면이 배지를 정할 때 보는 값 하나. <b>저장값이 아니라 파생이다</b> —
 * {@code status} + {@code expiresAt} + {@code deletedAt}의 조합(데이터 모델 §3).
 *
 * <p>삭제는 여기 없다. 삭제된 링크는 목록에서 사라지고 상세는 404다.
 * <b>방문자 입장에서는 셋이 전부 똑같이 보인다</b> — 구분은 관리 화면에만 있다(명세 §5.3).
 */
public enum LinkState {
    ACTIVE,
    DISABLED,
    EXPIRED
}
