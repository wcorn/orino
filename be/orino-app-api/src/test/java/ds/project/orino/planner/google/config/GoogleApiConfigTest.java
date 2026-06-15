package ds.project.orino.planner.google.config;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.planner.google.token.GoogleUnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GoogleApiConfig")
class GoogleApiConfigTest {

    @Nested
    @DisplayName("GoogleApiProperties 바인딩")
    class Binding {

        private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of())
                .withUserConfiguration(GoogleApiConfig.class);

        @Test
        @DisplayName("planner.google.* 프로퍼티를 record에 바인딩하고 RestClient 빈을 등록한다")
        void bindsPropertiesAndRegistersRestClient() {
            contextRunner
                    .withPropertyValues(
                            "planner.google.client-id=test-client-id",
                            "planner.google.client-secret=test-client-secret",
                            "planner.google.redirect-uri=https://api.orino.dev/api/planner/google/oauth/callback",
                            "planner.google.scopes[0]=https://www.googleapis.com/auth/calendar",
                            "planner.google.scopes[1]=https://www.googleapis.com/auth/tasks",
                            "planner.google.connect-timeout=3s",
                            "planner.google.read-timeout=7s")
                    .run(ctx -> {
                        assertThat(ctx).hasSingleBean(GoogleApiProperties.class);
                        assertThat(ctx).hasSingleBean(RestClient.class);

                        GoogleApiProperties props = ctx.getBean(GoogleApiProperties.class);
                        assertThat(props.clientId()).isEqualTo("test-client-id");
                        assertThat(props.clientSecret()).isEqualTo("test-client-secret");
                        assertThat(props.redirectUri())
                                .isEqualTo("https://api.orino.dev/api/planner/google/oauth/callback");
                        assertThat(props.scopes()).containsExactly(
                                "https://www.googleapis.com/auth/calendar",
                                "https://www.googleapis.com/auth/tasks");
                        assertThat(props.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
                        assertThat(props.readTimeout()).isEqualTo(Duration.ofSeconds(7));
                    });
        }
    }

    @Nested
    @DisplayName("RestClient defaultStatusHandler 매핑")
    class StatusHandler {

        private HttpServer server;
        private String baseUrl;
        private RestClient client;

        @BeforeEach
        void startStubServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/ok", ex -> respond(ex, 200, "{\"ok\":true}"));
            server.createContext("/server-error", ex -> respond(ex, 500, "{\"error\":\"backendError\"}"));
            server.createContext("/invalid-grant", ex -> respond(ex, 400, "{\"error\":\"invalid_grant\"}"));
            server.createContext("/unauthorized", ex -> respond(ex, 401, "{\"error\":\"unauthorized\"}"));
            server.start();
            baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

            GoogleApiProperties props = new GoogleApiProperties(
                    "cid", "secret", "http://localhost/cb",
                    List.of("scope"), Duration.ofSeconds(2), Duration.ofSeconds(5));
            client = new GoogleApiConfig().googleRestClient(props);
        }

        @AfterEach
        void stopStubServer() {
            server.stop(0);
        }

        @Test
        @DisplayName("2xx 응답은 예외 없이 본문을 반환한다")
        void success() {
            String body = client.get().uri(baseUrl + "/ok").retrieve().body(String.class);
            assertThat(body).contains("ok");
        }

        @Test
        @DisplayName("5xx 응답은 GOOGLE_API_FAILED(502)로 매핑한다")
        void serverErrorMapsToApiFailed() {
            assertThatThrownBy(() ->
                    client.get().uri(baseUrl + "/server-error").retrieve().body(String.class))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.GOOGLE_API_FAILED);
        }

        @Test
        @DisplayName("invalid_grant 본문을 가진 4xx 응답은 GOOGLE_INVALID_GRANT(401)로 매핑한다")
        void invalidGrantMapsToInvalidGrant() {
            assertThatThrownBy(() ->
                    client.get().uri(baseUrl + "/invalid-grant").retrieve().body(String.class))
                    .isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.GOOGLE_INVALID_GRANT);
        }

        @Test
        @DisplayName("401 응답은 GoogleUnauthorizedException으로 매핑한다(토큰 갱신 트리거)")
        void unauthorizedMapsToGoogleUnauthorized() {
            assertThatThrownBy(() ->
                    client.get().uri(baseUrl + "/unauthorized").retrieve().body(String.class))
                    .isInstanceOf(GoogleUnauthorizedException.class);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
