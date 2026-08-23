package ds.project.orino.planner.shortlink.og;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OG fetch의 방어선을 <b>진짜 HTTP로</b> 확인한다 — 리다이렉트 홉 검사, 본문 상한, 타입 제한.
 *
 * <p>목 서버는 루프백에 뜬다. 운영에서 루프백은 언제나 막히므로, 여기서만 쓰는
 * {@code SsrfGuard(properties, true)} 생성자로 그 한 겹을 연다. <b>나머지 대역은 그대로 막혀
 * 있어서</b>, 공개 → 사설로 튀는 리다이렉트 검사가 의미를 갖는다.
 */
class OgPreviewClientTest {

    private static final String HTML = "text/html; charset=utf-8";

    private MockWebServer server;
    private OgPreviewClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        OgPreviewProperties properties = new OgPreviewProperties(
                true, Duration.ofSeconds(3), 1024, 3, List.of());
        client = new OgPreviewClient(SsrfGuard.allowingLoopback(properties), properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.close();
    }

    @Test
    @DisplayName("HTML을 읽어 og:title·og:image를 뽑는다")
    void readsOgTags() {
        server.enqueue(new MockResponse.Builder()
                .addHeader("Content-Type", HTML)
                .body("""
                        <html><head>
                        <meta property="og:title" content="2026 여름 가족사진.zip">
                        <meta content="https://img.example.com/thumb.png" property="og:image">
                        </head></html>
                        """)
                .build());

        OgPreviewResult result = client.fetchHtml(url("/")).map(OgHtmlParser::parse).orElseThrow();

        assertThat(result.title()).isEqualTo("2026 여름 가족사진.zip");
        assertThat(result.imageUrl()).isEqualTo("https://img.example.com/thumb.png");
    }

    @Test
    @DisplayName("공개 → 사설로 튀는 리다이렉트는 2홉째에 막힌다")
    void blocksRedirectToPrivateAddress() {
        server.enqueue(new MockResponse.Builder()
                .code(302)
                .addHeader("Location", "http://169.254.169.254/latest/meta-data/")
                .build());

        assertThat(client.fetchHtml(url("/"))).isEmpty();
        // 1홉만 나갔다 — 메타데이터 주소로는 커넥션을 열지 않았다.
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("리다이렉트가 3홉을 넘으면 포기한다")
    void stopsAfterMaxRedirects() {
        for (int i = 0; i < 5; i++) {
            server.enqueue(new MockResponse.Builder()
                    .code(302)
                    .addHeader("Location", "/next")
                    .build());
        }

        assertThat(client.fetchHtml(url("/"))).isEmpty();
        assertThat(server.getRequestCount()).isLessThanOrEqualTo(4);
    }

    @Test
    @DisplayName("본문이 상한을 넘으면 거기서 끊는다")
    void truncatesLargeBody() {
        server.enqueue(new MockResponse.Builder()
                .addHeader("Content-Type", HTML)
                .body("<html><head>" + "x".repeat(4096) + "</head></html>")
                .build());

        Optional<String> html = client.fetchHtml(url("/"));

        assertThat(html).isPresent();
        assertThat(html.get().length()).isLessThanOrEqualTo(1024);
    }

    @Test
    @DisplayName("HTML이 아니면 읽지 않는다 — 이미지·바이너리를 파싱하러 가지 않는다")
    void ignoresNonHtml() {
        server.enqueue(new MockResponse.Builder()
                .addHeader("Content-Type", "application/pdf")
                .body("%PDF-1.7")
                .build());

        assertThat(client.fetchHtml(url("/"))).isEmpty();
    }

    @Test
    @DisplayName("http·https가 아닌 스킴은 요청 자체를 만들지 않는다")
    void rejectsOtherSchemes() {
        assertThat(client.fetchHtml("file:///etc/passwd")).isEmpty();
        assertThat(client.fetchHtml("gopher://example.com")).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("실패해도 예외가 아니라 빈 값이다 — 사유가 밖으로 나가지 않는다")
    void returnsEmptyOnFailure() {
        server.enqueue(new MockResponse.Builder().code(500).build());

        assertThat(client.fetchHtml(url("/"))).isEmpty();
    }

    private String url(String path) {
        return server.url(path).toString();
    }
}
