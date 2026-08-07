package ds.project.orino.common.exception;

public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 클라이언트가 다음 행동을 정하는 데 쓸 부가 값. 대부분의 에러는 비어 있다.
     * 응답에는 값이 있을 때만 실린다({@link ErrorResponse}).
     */
    private final Object data;

    public CustomException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null);
    }

    /** 사용자에게 어디가 틀렸는지 알려야 하는 경우(예: 수식 문법 오류)에 상세를 덧붙인다. */
    public CustomException(ErrorCode errorCode, String detail) {
        this(errorCode, errorCode.getMessage(detail), null);
    }

    public CustomException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(cause), cause);
        this.errorCode = errorCode;
        this.data = null;
    }

    private CustomException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }

    /**
     * 값을 실어 보내는 에러. 예: 기간 단축 확인(409)에서 보관함으로 이동할 일정 개수 —
     * 클라이언트가 메시지 문자열을 파싱해 숫자를 뽑게 두지 않는다.
     */
    public static CustomException withData(ErrorCode errorCode, Object data) {
        return new CustomException(errorCode, errorCode.getMessage(), data);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object getData() {
        return data;
    }
}
