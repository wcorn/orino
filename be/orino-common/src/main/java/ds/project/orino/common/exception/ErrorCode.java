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

    // PLANNER (Google Calendar 연동)
    GOOGLE_NOT_CONNECTED("PLN-ERR-003", "Google 연동이 필요합니다.", 409),
    GOOGLE_API_FAILED("PLN-ERR-004", "Google API 호출에 실패했습니다.", 502),
    GOOGLE_INVALID_GRANT("PLN-ERR-005", "Google 연동이 만료되어 재연결이 필요합니다.", 401);

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
