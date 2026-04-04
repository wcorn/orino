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
    REFERENCED_DATA_EXISTS("SP-ERR-003", "참조하는 데이터가 존재하여 삭제할 수 없습니다.", 409),
    INVALID_STATE("SP-ERR-004", "현재 상태에서 수행할 수 없는 작업입니다.", 409);

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
