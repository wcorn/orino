package ds.project.orino.core.web;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.common.exception.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("handleException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<?> handleCustomException(CustomException e) {
        log.error("handleCustomException: {}", e.getErrorCode().toString());
        // 값을 실은 에러만 data가 붙은 형태로 나간다. 나머지는 기존 {code, message} 그대로.
        Object body = e.getData() != null ? DataErrorResponse.of(e) : ErrorResponse.of(e);
        return ResponseEntity.status(e.getErrorCode().getHttpStatus()).body(body);
    }

    /**
     * 매핑이 없는 경로. <b>500이 아니라 404다.</b>
     *
     * <p>없는 주소를 부른 것은 <b>클라이언트 쪽 사실</b>이고, 서버가 잘못한 것과는 다루는
     * 법이 다르다 — 500은 알림·대시보드·SLO 에러율에 잡히고 404는 안 잡힌다. 그대로 두면
     * 오타 하나가 장애 지표를 올리고, 반대로 진짜 5xx가 그 사이에 묻힌다.
     *
     * <p>이 앱에서 특히 그렇다. 새 버전을 사용자가 받아들여야 갈리므로
     * ({@code registerType: "prompt"}) 사라진 API를 부르는 옛 번들·서비스워커 캐시가
     * 한동안 남는다 — 가계부를 지운 직후(#1405)가 정확히 그 상황이다.
     *
     * <p><b>ERROR로 찍지 않는다.</b> 로그를 보는 이유는 고칠 것을 찾기 위해서인데 여기에는
     * 고칠 것이 없다. 몇 번 왔는지는 상태 코드별 지표가 이미 세고 있다.
     *
     * <p>{@code NoHandlerFoundException}은 받지 않는다. 정적 리소스 매핑이 켜져 있는
     * 기본 설정에서는 그쪽이 아니라 이 예외가 난다 — 안 나는 예외에 분기를 두면 다음 사람이
     * 그게 도는 줄 안다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("handleNoResourceFound: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        log.error("handleMethodNotSupported: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(
            MethodArgumentNotValidException e) {
        log.error("handleValidationError: {}", e.getMessage());
        String message = e.getBindingResult().getAllErrors()
                .get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.BAD_REQUEST, message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {
        log.error("handleTypeMismatch: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.BAD_REQUEST, e));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException e) {
        log.error("handleMissingParameter: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.BAD_REQUEST, e.getParameterName() + " 파라미터가 필요합니다."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException e) {
        log.error("handleMessageNotReadable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.BAD_REQUEST));
    }
}
