package ds.project.orino.common.exception;

public class ErrorResponse {

    private final String code;
    private final String message;

    private ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, Exception e) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(e));
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(message));
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
