package ds.project.orino.planner.google.routine;

/**
 * 루틴 종류. Google 이벤트의 {@code extendedProperties.private.orinoRoutineType} 값에 대응한다.
 *
 * <ul>
 *   <li>{@link #HABIT} — 종일 체크형 습관(통합 피드에서 완료 체크 가능)</li>
 *   <li>{@link #SCHEDULE} — 시간 고정 일정(시간 블록으로 렌더, 체크 없음)</li>
 * </ul>
 */
public enum RoutineType {
    HABIT("habit"),
    SCHEDULE("schedule");

    private final String code;

    RoutineType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** {@code "habit"|"schedule"} 코드를 enum으로 파싱한다. 알 수 없으면 IllegalArgumentException. */
    public static RoutineType fromCode(String code) {
        for (RoutineType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("알 수 없는 routine type: " + code);
    }
}
