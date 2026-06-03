package ds.project.orino.core.security;

import ds.project.orino.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증되지 않은(또는 토큰이 만료된) 요청에 대해 401과 ErrorResponse JSON을 반환한다.
 * <p>
 * 기본 Spring Security는 미인증 요청에 403을 반환하는데, FE 인터셉터는 401에만
 * 토큰 자동 재발급을 수행한다. 인증 실패를 401로 통일해 access token 만료 시
 * 런타임에서도 자동 재발급이 동작하도록 한다.
 * <p>
 * core-web 모듈에는 ObjectMapper 빈이 없으므로(웹 MVC 자동설정 미적용 라이브러리 모듈),
 * 의존성을 추가하지 않고 ErrorResponse와 동일한 형태의 JSON을 직접 작성한다.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode errorCode = ErrorCode.INVALID_TOKEN;
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"code\":\"" + errorCode.getCode() + "\",\"message\":\"" + errorCode.getMessage() + "\"}");
    }
}
