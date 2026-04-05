package ds.project.orino.planner.scheduling.engine.model;

/**
 * 스케줄링 대상 항목의 분류.
 * 기본 우선순위 순서대로 정의한다 (1이 가장 높음).
 */
public enum ItemCategory {
    OVERDUE_REVIEW,
    TODAY_REVIEW,
    URGENT_TODO,
    DEADLINE_STUDY,
    NEW_STUDY,
    NO_DEADLINE_TODO
}
