package ds.project.orino.domain.planner.push.entity;

/** 알림 종류(§4.2 · §4.3). */
public enum NotificationType {
    /** 일정 시작 전. 시작시각 − notifyMinutes. */
    ACTIVITY,
    /** 출발 시각. 시작시각 − 이동시간 − 5분. 직전 장소 있는 일정이 필요하다. */
    DEPARTURE,
    /** 여행 기간 중 매일 현지 08:00. 그날 일정이 0건이면 보내지 않는다. */
    MORNING_SUMMARY,
    /**
     * 출발 전날 현지 09:00, 여행당 <b>하나</b>(v2.2 §14). 남은 준비가 0개면 보내지 않는다.
     *
     * <p>D-7·D-3·D-1로 나눠 보내지 않는 이유가 있다 — 그렇게 되면 사용자는 준비 알림을
     * 무시하기 시작하고, 그 습관이 정작 필요한 여행 중 일정 알림까지 함께 데려간다(D-32).
     */
    PREP_REMINDER
}
