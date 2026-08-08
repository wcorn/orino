package ds.project.orino.domain.planner.push.entity;

/** 알림 종류(§4.2 · §4.3). */
public enum NotificationType {
    /** 일정 시작 전. 시작시각 − notifyMinutes. */
    ACTIVITY,
    /** 출발 시각. 시작시각 − 이동시간 − 5분. 직전 장소 있는 일정이 필요하다. */
    DEPARTURE,
    /** 여행 기간 중 매일 현지 08:00. 그날 일정이 0건이면 보내지 않는다. */
    MORNING_SUMMARY
}
