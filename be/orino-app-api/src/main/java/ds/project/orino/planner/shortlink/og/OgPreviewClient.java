package ds.project.orino.planner.shortlink.og;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * OG 프리뷰 fetch(아키텍처 §5).
 *
 * <p><b>리다이렉트를 OkHttp에 맡기지 않는다.</b> 자동으로 따라가면 2홉째 주소는 검사를 거치지
 * 않고 열린다 — 공개 도메인에서 사설 IP로 튀는 리다이렉트가 정확히 그 구멍을 쓴다.
 * 그래서 {@code followRedirects(false)}로 두고 <b>홉마다 직접 검사한 뒤</b> 다음 요청을 만든다.
 *
 * <p>연결은 {@link SsrfGuard}가 통과시킨 IP로만 한다. OkHttp의 {@code Dns}를 갈아끼우면
 * <b>호스트명은 그대로</b>(TLS SNI·Host 헤더가 살아 있다) 연결 대상 IP만 우리가 고른 것이 된다.
 */
@Component
public class OgPreviewClient {

    private static final String USER_AGENT = "orino-shortlink-preview/1.0 (+https://orino.dev)";

    private final SsrfGuard guard;
    private final OgPreviewProperties properties;
    private final OkHttpClient http;

    public OgPreviewClient(SsrfGuard guard, OgPreviewProperties properties) {
        this.guard = guard;
        this.properties = properties;
        this.http = new OkHttpClient.Builder()
                .callTimeout(properties.timeout())
                .connectTimeout(properties.timeout())
                .readTimeout(properties.timeout())
                // 홉마다 검사해야 하므로 자동 추적을 끈다.
                .followRedirects(false)
                .followSslRedirects(false)
                .dns(new GuardedDns())
                .build();
    }

    /**
     * @return 읽어 낸 HTML. 스킴·주소·타입·크기 중 하나라도 어긋나면 비어 있다
     */
    public Optional<String> fetchHtml(String url) {
        HttpUrl current = parse(url);
        for (int hop = 0; hop <= properties.maxRedirects(); hop++) {
            if (current == null) {
                return Optional.empty();
            }
            try (Response response = execute(current)) {
                if (response == null) {
                    return Optional.empty();
                }
                if (response.isRedirect()) {
                    String location = response.header("Location");
                    // 상대 경로 리다이렉트도 있다. 지금 URL 기준으로 풀어낸 뒤 다시 검사한다.
                    current = location == null ? null : current.resolve(location);
                    continue;
                }
                return body(response);
            }
        }
        // 홉을 다 썼다. 여기까지 왔다는 것은 리다이렉트 고리에 걸렸다는 뜻이다.
        return Optional.empty();
    }

    /** 스킴은 http·https만. 그 외(`file:`·`gopher:` 등)는 여기서 끝난다. */
    private HttpUrl parse(String url) {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) {
            return null;
        }
        String scheme = parsed.scheme().toLowerCase(Locale.ROOT);
        return scheme.equals("http") || scheme.equals("https") ? parsed : null;
    }

    private Response execute(HttpUrl url) {
        try {
            // 요청을 보내기 전에 이 홉의 주소를 검사한다. Dns가 한 번 더 보지만,
            // 여기서 막으면 커넥션을 열지도 않는다.
            guard.resolveSafely(url.host());
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .build();
            return http.newCall(request).execute();
        } catch (IOException | RuntimeException e) {
            // BlockedHostException도 여기로 온다(RuntimeException).
            // 사유를 남기지 않는다 — 응답에 실리면 그게 곧 내부망 스캐너의 눈이다.
            return null;
        }
    }

    /**
     * HTML일 때만, <b>상한까지만</b> 읽는다. 응답이 1MB를 넘으면 거기서 끊고 읽은 데까지 쓴다 —
     * OG 태그는 {@code <head>}에 있으므로 앞부분만 있어도 대개 충분하다.
     */
    private Optional<String> body(Response response) {
        if (!response.isSuccessful()) {
            return Optional.empty();
        }
        ResponseBody responseBody = response.body();
        String contentType = response.header("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("html")) {
            return Optional.empty();
        }
        try {
            return Optional.of(responseBody.source()
                    .readString(readableBytes(responseBody), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private long readableBytes(ResponseBody body) throws IOException {
        long limit = properties.maxBodyBytes();
        // 상한 + 1바이트까지 당겨 보고, 그보다 크면 상한에서 자른다.
        body.source().request(limit + 1);
        return Math.min(limit, body.source().getBuffer().size());
    }

    /** 검사에 통과한 주소만 돌려준다 — OkHttp는 이 목록으로만 연결한다. */
    private final class GuardedDns implements Dns {
        @Override
        public List<InetAddress> lookup(String hostname) {
            return guard.resolveSafely(hostname);
        }
    }
}
