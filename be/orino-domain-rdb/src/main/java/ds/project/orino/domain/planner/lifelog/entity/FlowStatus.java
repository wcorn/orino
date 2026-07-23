package ds.project.orino.domain.planner.lifelog.entity;

/**
 * 흐름 상태.
 *
 * <ul>
 *     <li>{@link #ACTIVE} — 진행 중(기본). 목록 상단.</li>
 *     <li>{@link #ARCHIVED} — 보관. 목록에서 접어둔다.</li>
 * </ul>
 */
public enum FlowStatus {
    ACTIVE,
    ARCHIVED
}
