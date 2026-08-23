package ds.project.orino.domain.planner.shortlink.entity;

/**
 * 링크의 저장된 상태. <b>만료는 여기 없다</b> — 만료는 {@code expires_at}과 현재 시각의
 * 비교로 파생한다. 저장하면 날짜가 넘어갈 때 갱신할 주체가 없다(데이터 모델 §3).
 *
 * <p>{@link #DELETED}는 소프트 삭제다. 행이 남아 있어야 {@code UNIQUE(slug)}가 슬러그
 * 재사용을 막는다(명세 §3.1).
 */
public enum ShortlinkStatus {
    ACTIVE,
    DISABLED,
    DELETED
}
