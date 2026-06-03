package ds.project.orino.core.time;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * {@code X-Timezone} 헤더(IANA 시간대, 예: {@code Asia/Seoul})를 읽어
 * 요청 단위로 {@link UserTimeZone}에 설정한다. 헤더가 없거나 유효하지 않으면
 * 기본값({@link UserTimeZone#DEFAULT})을 사용한다.
 */
@Component
public class UserTimeZoneInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Timezone";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserTimeZone.set(resolve(request.getHeader(HEADER)));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserTimeZone.clear();
    }

    private ZoneId resolve(String header) {
        if (header == null || header.isBlank()) {
            return UserTimeZone.DEFAULT;
        }
        try {
            return ZoneId.of(header.trim());
        } catch (DateTimeException e) {
            return UserTimeZone.DEFAULT;
        }
    }
}
