package ds.project.orino.domain.planner.lifelog.entity;

/**
 * 순간의 기분. 선택 값이라 {@link Moment#getMood()}는 null일 수 있다.
 */
public enum Mood {
    HAPPY,
    CALM,
    EXCITED,
    TIRED,
    SAD
}
