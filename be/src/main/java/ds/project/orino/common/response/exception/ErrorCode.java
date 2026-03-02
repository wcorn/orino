package ds.project.orino.common.response.exception;

public enum ErrorCode {

    // GLOBAL
    BAD_REQUEST("GLB-ERR-001", "잘못된 요청입니다."),
    METHOD_NOT_ALLOWED("GLB-ERR-002", "허용되지 않은 메서드입니다."),
    INTERNAL_SERVER_ERROR("GLB-ERR-003", "내부 서버 오류입니다.");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
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

    public String getMessage(String additionalMessage) {
        if (additionalMessage != null && !additionalMessage.isBlank()) {
            return message + " - " + additionalMessage;
        }
        return message;
    }
}
