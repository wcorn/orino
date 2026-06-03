package ds.project.orino.core.time;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import tools.jackson.databind.module.SimpleModule;

import java.time.Instant;

/**
 * 사용자 시간대 처리를 위한 웹 설정.
 * <ul>
 *   <li>{@link UserTimeZoneInterceptor}를 모든 요청에 등록한다.</li>
 *   <li>{@link Instant} 직렬화를 사용자 시간대 offset 포함 ISO로 커스터마이즈한다.</li>
 * </ul>
 * Spring Boot 4 / Jackson 3 기반. {@link JsonMapperBuilderCustomizer}로 기본 JsonMapper에
 * Instant 직렬화 모듈을 추가해 표준 Instant 직렬화({@code ...Z})를 덮어쓴다.
 */
@Configuration
public class UserTimeZoneWebConfig implements WebMvcConfigurer {

    private final UserTimeZoneInterceptor userTimeZoneInterceptor;

    public UserTimeZoneWebConfig(UserTimeZoneInterceptor userTimeZoneInterceptor) {
        this.userTimeZoneInterceptor = userTimeZoneInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userTimeZoneInterceptor);
    }

    @Bean
    public JsonMapperBuilderCustomizer instantToUserZoneCustomizer() {
        SimpleModule module = new SimpleModule("InstantToUserZoneModule");
        module.addSerializer(Instant.class, new InstantToUserZoneSerializer());
        return builder -> builder.addModule(module);
    }
}
