package ds.project.orino.planner.google.config;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Google API 호출용 {@link RestClient} 빈.
 *
 * <p>무거운 google-api-java-client 대신 Spring {@code RestClient} 로 직접 호출한다
 * (의존성 0 추가, Jackson 재사용). OAuth 토큰 엔드포인트와 Calendar/Tasks 엔드포인트는
 * 호스트가 다르므로 baseUrl 을 두지 않고 호출부에서 절대 URI 를 지정한다.
 *
 * <p>{@code defaultStatusHandler} 가 4xx/5xx 응답을 {@link CustomException} 으로 매핑한다.
 * 응답 본문에 {@code invalid_grant} 가 있으면 재연동 필요(401)로, 그 외는 Google API 실패(502)로 본다.
 */
@Configuration
@EnableConfigurationProperties(GoogleApiProperties.class)
public class GoogleApiConfig {

    @Bean
    public RestClient googleRestClient(GoogleApiProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());

        return RestClient.builder()
                .requestFactory(factory)
                .defaultStatusHandler(
                        statusCode -> statusCode.isError(),
                        (request, response) -> {
                            String body = readBody(response);
                            if (body.contains("invalid_grant")) {
                                throw new CustomException(ErrorCode.GOOGLE_INVALID_GRANT);
                            }
                            throw new CustomException(ErrorCode.GOOGLE_API_FAILED);
                        })
                .build();
    }

    private String readBody(org.springframework.http.client.ClientHttpResponse response) {
        try {
            return StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
