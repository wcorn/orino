package ds.project.orino.planner.google.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.time.Duration;

/**
 * Google API 호출의 견고성: 429/5xx에 짧은 지수 백오프로 1~2회 재시도하고,
 * 호출 결과를 {@code google.api.calls{result}} 카운터로 집계한다.
 *
 * <p>5xx는 결과가 불확실하므로 멱등이 아닌 POST(생성)는 재시도하지 않는다(중복 생성 방지).
 * 429(레이트리밋)는 요청이 처리되지 않았으므로 모든 메서드에 재시도한다.
 */
public class GoogleApiRetryInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GoogleApiRetryInterceptor.class);
    private static final int TOO_MANY_REQUESTS = 429;
    private static final String METRIC_NAME = "google.api.calls";

    private final MeterRegistry meterRegistry;
    private final int maxAttempts;
    private final Duration baseBackoff;

    public GoogleApiRetryInterceptor(MeterRegistry meterRegistry, int maxAttempts, Duration baseBackoff) {
        this.meterRegistry = meterRegistry;
        this.maxAttempts = maxAttempts;
        this.baseBackoff = baseBackoff;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        int retries = 0;
        while (true) {
            ClientHttpResponse response;
            try {
                response = execution.execute(request, body);
            } catch (IOException e) {
                record("error");
                throw e;
            }

            int status = response.getStatusCode().value();
            if (canRetry(status, request.getMethod()) && retries < maxAttempts) {
                retries++;
                response.close();
                backoff(retries);
                log.warn("Google API {} {} → {} 재시도 {}/{}",
                        request.getMethod(), request.getURI(), status, retries, maxAttempts);
                continue;
            }

            record(resultOf(status));
            return response;
        }
    }

    private boolean canRetry(int status, HttpMethod method) {
        if (status == TOO_MANY_REQUESTS) {
            return true; // 레이트리밋: 요청이 처리되지 않음 → 모든 메서드 재시도 안전
        }
        if (status < 500) {
            return false;
        }
        // 5xx: 처리 여부 불확실 → POST(생성)는 중복 방지 위해 재시도 안 함
        return method != HttpMethod.POST;
    }

    private void backoff(int attempt) {
        long millis = baseBackoff.toMillis() * (1L << (attempt - 1));
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void record(String result) {
        meterRegistry.counter(METRIC_NAME, "result", result).increment();
    }

    private static String resultOf(int status) {
        if (status >= 500) {
            return "server_error";
        }
        if (status >= 400) {
            return "client_error";
        }
        return "success";
    }
}
