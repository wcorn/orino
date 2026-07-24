package ds.project.orino.common.exception;

public enum ErrorCode {

    // GLOBAL
    BAD_REQUEST("GLB-ERR-001", "잘못된 요청입니다.", 400),
    METHOD_NOT_ALLOWED("GLB-ERR-002", "허용되지 않은 메서드입니다.", 405),
    INTERNAL_SERVER_ERROR("GLB-ERR-003", "내부 서버 오류입니다.", 500),

    // AUTH
    INVALID_CREDENTIALS("AUTH-ERR-001", "아이디 또는 비밀번호가 올바르지 않습니다.", 401),
    INVALID_TOKEN("AUTH-ERR-002", "유효하지 않은 토큰입니다.", 401),

    // STUDY PLANNER
    RESOURCE_NOT_FOUND("SP-ERR-001", "존재하지 않는 리소스입니다.", 404),
    INVALID_REQUEST("SP-ERR-002", "유효하지 않은 요청입니다.", 400),
    INVALID_STATE("SP-ERR-003", "현재 상태에서 수행할 수 없는 작업입니다.", 409),
    DUPLICATE_COLUMN_LABEL("SP-ERR-004", "이미 있는 열 이름입니다.", 409),
    FORMULA_SYNTAX_ERROR("SP-ERR-005", "수식을 이해할 수 없습니다.", 400),
    FORMULA_CIRCULAR_REFERENCE("SP-ERR-006", "수식이 자기 자신을 참조합니다.", 409),
    FORMULA_PROPAGATION_TOO_WIDE("SP-ERR-007", "이 편집이 다시 계산할 수식이 너무 많습니다.", 409),

    // PLANNER (Google Calendar 연동)
    ROUTINE_INVALID_RULE("PLN-ERR-002", "유효하지 않은 반복 규칙입니다.", 400),
    GOOGLE_NOT_CONNECTED("PLN-ERR-003", "Google 연동이 필요합니다.", 409),
    GOOGLE_API_FAILED("PLN-ERR-004", "Google API 호출에 실패했습니다.", 502),
    GOOGLE_INVALID_GRANT("PLN-ERR-005", "Google 연동이 만료되어 재연결이 필요합니다.", 401),

    // LIFELOG (일상기록)
    LIFELOG_GEOCODING_FAILED("LIFELOG-ERR-001", "장소 정보를 가져오지 못했습니다.", 502),
    LIFELOG_MOMENT_NOT_FOUND("LIFELOG-ERR-002", "존재하지 않는 기록입니다.", 404),
    LIFELOG_EMPTY_MOMENT("LIFELOG-ERR-003", "본문이나 사진 중 하나는 있어야 합니다.", 400),
    LIFELOG_INVALID_COORDINATE("LIFELOG-ERR-004", "위도와 경도는 함께 지정해야 합니다.", 400),
    LIFELOG_FLOW_NOT_FOUND("LIFELOG-ERR-005", "존재하지 않는 흐름입니다.", 404);

    private final String code;
    private final String message;
    private final int httpStatus;

    ErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getMessage(Throwable e) {
        return message + " - " + e.getMessage();
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getMessage(String additionalMessage) {
        if (additionalMessage != null && !additionalMessage.isBlank()) {
            return message + " - " + additionalMessage;
        }
        return message;
    }
}
