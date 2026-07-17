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

    /**
     * 예외가 들고 있는 메시지를 그대로 쓴다. {@code CustomException(errorCode, detail)}로
     * 덧붙인 상세(예: "없는 열: 점수")를 사용자에게 전하기 위한 경로 —
     * {@link #of(ErrorCode)}만 쓰면 그 상세가 응답에서 사라진다.
     */
    public static ErrorResponse of(CustomException e) {
        return new ErrorResponse(e.getErrorCode().getCode(), e.getMessage());
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
