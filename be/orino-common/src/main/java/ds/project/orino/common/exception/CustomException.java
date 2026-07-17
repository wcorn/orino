package ds.project.orino.common.exception;

public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 사용자에게 어디가 틀렸는지 알려야 하는 경우(예: 수식 문법 오류)에 상세를 덧붙인다. */
    public CustomException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage(detail));
        this.errorCode = errorCode;
    }

    public CustomException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(cause), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
