package ds.project.orino.domain.planner.shortlink.entity;

/**
 * 기기 구분. <b>User-Agent 원문은 저장하지 않는다</b> — 판정 결과만 남긴다(명세 §8.1).
 *
 * <p>{@link #UNKNOWN}은 판정에 실패했다는 뜻이고, 화면은 이것을 비율에서 빼지 않는다 —
 * 모르는 것을 없는 것처럼 만들면 합이 100%가 되면서 정확해 보이는 착시가 생긴다.
 */
public enum VisitDevice {
    MOBILE,
    DESKTOP,
    TABLET,
    UNKNOWN
}
