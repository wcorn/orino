package ds.project.orino.planner.google.config;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.planner.google.token.GoogleUnauthorizedException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

/**
 * Google API 호출용 {@link RestClient} 빈.
 *
 * <p>무거운 google-api-java-client 대신 Spring {@code RestClient} 로 직접 호출한다
 * (의존성 0 추가, Jackson 재사용). OAuth 토큰 엔드포인트와 Calendar/Tasks 엔드포인트는
 * 호스트가 다르므로 baseUrl 을 두지 않고 호출부에서 절대 URI 를 지정한다.
 *
 * <p>{@code defaultStatusHandler} 가 4xx/5xx 응답을 매핑한다: API 401 → {@link GoogleUnauthorizedException}
 * (access token 만료, {@code executeWithRetry}가 1회 갱신 후 재시도) / 본문에 {@code invalid_grant} →
 * 재연동 필요(PLN-ERR-005) / 그 외 → Google API 실패(PLN-ERR-004).
 */
@Configuration
@EnableConfigurationProperties({GoogleApiProperties.class, GoogleOAuthProperties.class})
public class GoogleApiConfig {

    @Bean
    public RestClient googleRestClient(GoogleApiProperties props) {
        // JDK HttpURLConnection(SimpleClientHttpRequestFactory)은 PATCH를 지원하지 않으므로
        // java.net.http 기반 JdkClientHttpRequestFactory를 쓴다(의존성 추가 없음, PATCH 지원).
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.readTimeout());

        return RestClient.builder()
                .requestFactory(factory)
                .defaultStatusHandler(
                        statusCode -> statusCode.isError(),
                        (request, response) -> {
                            int statusCode = response.getStatusCode().value();
                            if (statusCode == 401) {
                                throw new GoogleUnauthorizedException();
                            }
                            if (statusCode == 404) {
                                throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
                            }
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
