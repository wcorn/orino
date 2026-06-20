package ds.project.orino.planner.google.routine;

import java.util.Map;

/**
 * 루틴 이벤트 식별을 위한 {@code extendedProperties.private} 태깅 헬퍼.
 *
 * <p>{@code orinoRoutine=1} 센티넬로 루틴 이벤트를 구분하고
 * {@code events.list?privateExtendedProperty=orinoRoutine=1}로 필터링한다.
 * {@code orinoRoutineType}으로 습관/일정을 구분한다.
 */
public final class RoutineTag {

    public static final String KEY_ROUTINE = "orinoRoutine";
    public static final String KEY_ROUTINE_TYPE = "orinoRoutineType";
    public static final String ROUTINE_FLAG = "1";
    /** following 분할로 생성된 시리즈의 분할 출처 마커(idempotent 복구용): {@code {oldMasterId}@{instanceDate}}. */
    public static final String KEY_SPLIT_OF = "orinoRoutineSplitOf";

    /** {@code events.list}의 privateExtendedProperty 필터 값: {@code orinoRoutine=1}. */
    public static final String LIST_FILTER = KEY_ROUTINE + "=" + ROUTINE_FLAG;

    private RoutineTag() {
    }

    /** 주어진 종류로 루틴 태그 private 프로퍼티 맵을 만든다. */
    public static Map<String, String> privateProperties(RoutineType type) {
        return Map.of(
                KEY_ROUTINE, ROUTINE_FLAG,
                KEY_ROUTINE_TYPE, type.code());
    }

    /** 분할 마커를 포함한 루틴 태그 맵. following 분할로 생성된 새 시리즈에 사용한다. */
    public static Map<String, String> splitProperties(RoutineType type, String splitMarker) {
        return Map.of(
                KEY_ROUTINE, ROUTINE_FLAG,
                KEY_ROUTINE_TYPE, type.code(),
                KEY_SPLIT_OF, splitMarker);
    }

    /** following 분할 마커 값: {@code {oldMasterId}@{instanceDate}}. */
    public static String splitMarker(String oldMasterId, String instanceDate) {
        return oldMasterId + "@" + instanceDate;
    }

    /** private 프로퍼티 맵이 루틴 이벤트를 가리키는지(센티넬 보유) 판별한다. */
    public static boolean isRoutine(Map<String, String> privateProperties) {
        return privateProperties != null
                && ROUTINE_FLAG.equals(privateProperties.get(KEY_ROUTINE));
    }

    /** private 프로퍼티에서 루틴 종류를 읽는다. 없거나 알 수 없으면 null. */
    public static RoutineType routineType(Map<String, String> privateProperties) {
        if (privateProperties == null) {
            return null;
        }
        String code = privateProperties.get(KEY_ROUTINE_TYPE);
        if (code == null) {
            return null;
        }
        try {
            return RoutineType.fromCode(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
