package ds.project.orino.planner.google.routine;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;

/**
 * 시리즈 편집/삭제 적용 범위.
 *
 * <ul>
 *   <li>{@link #ALL} — 전체 시리즈(마스터)</li>
 *   <li>{@link #FOLLOWING} — 이 인스턴스 이후 모두(마스터 UNTIL 분할 + 새 시리즈)</li>
 *   <li>{@link #INSTANCE} — 이 인스턴스 하나(예외 처리)</li>
 * </ul>
 *
 * <p>{@link #FOLLOWING}·{@link #INSTANCE}는 {@code instanceDate}가 필요하다.
 */
public enum RoutineScope {
    ALL,
    FOLLOWING,
    INSTANCE;

    /** 쿼리 파라미터 문자열을 enum으로 파싱한다. 알 수 없으면 400. */
    public static RoutineScope from(String scope) {
        try {
            return RoutineScope.valueOf(scope.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, e);
        }
    }

    public boolean requiresInstanceDate() {
        return this == FOLLOWING || this == INSTANCE;
    }
}
