package ds.project.orino.core.web;

import ds.project.orino.common.exception.CustomException;

/**
 * 값을 함께 실어 보내는 에러 응답. 클라이언트가 다음 행동을 정하려면 숫자가 필요한 경우에 쓴다 —
 * 기간 단축 확인(409)의 "보관함으로 이동할 일정 개수"가 그 예다.
 *
 * <p>{@code ErrorResponse}에 필드를 더하지 않고 별도 타입으로 둔 이유가 둘 있다.
 * 하나는 {@code orino-common}이 의존성 없는 순수 Java 모듈이라 Jackson 애너테이션
 * ({@code @JsonInclude})을 쓸 수 없다는 것, 다른 하나는 필드를 더하면 값이 없는 대다수 에러에도
 * {@code "data": null}이 붙어 <b>전 엔드포인트의 에러 형태가 바뀐다</b>는 것이다.
 * 값이 있을 때만 이 타입으로 응답하므로 기존 에러 응답은 그대로다.
 */
public record DataErrorResponse(String code, String message, Object data) {

    public static DataErrorResponse of(CustomException e) {
        return new DataErrorResponse(e.getErrorCode().getCode(), e.getMessage(), e.getData());
    }
}
