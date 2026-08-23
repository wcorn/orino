package ds.project.orino.planner.shortlink;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게이트웨이 404와 BE 404가 <b>같은 화면</b>인지 고정한다(결정 기록 D-7).
 *
 * <p>슬러그 문자셋 밖 요청은 BE에 닿기 전에 Envoy가 직접 404를 낸다. 그래서 같은 HTML이 두
 * 곳에 존재하고, 어긋나는 순간 <b>그 차이 자체가 "이 슬러그는 형식이 맞다"는 정보</b>가 된다
 * (명세 §7). 중복을 감수하는 대신 어긋남을 여기서 잡는다.
 *
 * <p>이 테스트는 {@code be/**} 변경에서 도는 BE CI에 걸린다. 게이트웨이 쪽 파일만 고치는
 * 변경은 Infra CI만 돌므로, 두 파일을 함께 고치는 것이 규칙이다 — 그래서 실패 메시지에
 * 두 경로를 모두 적는다.
 */
class ShortlinkFailurePageParityTest {

    /** 테스트 작업 디렉터리는 {@code be/orino-app-api}다. */
    private static final Path GATEWAY_COPY =
            Path.of("../../infra/helm/istio-gateway/files/shortlink-404.html");
    private static final Path GATEWAY_TEMPLATE =
            Path.of("../../infra/helm/istio-gateway/templates/virtualservice-shortlink.yaml");

    @Test
    @DisplayName("BE 실패 화면과 게이트웨이 directResponse HTML이 바이트 단위로 같다")
    void gatewayCopyMatchesBackendPage() throws IOException {
        String backend = new ClassPathResource("shortlink/404.html")
                .getContentAsString(StandardCharsets.UTF_8);
        String gateway = Files.readString(GATEWAY_COPY, StandardCharsets.UTF_8);

        assertThat(gateway)
                .withFailMessage("""
                        게이트웨이 404와 BE 404가 어긋났다(D-7).
                        두 파일을 함께 고쳐야 한다:
                          be/orino-app-api/src/main/resources/shortlink/404.html
                          infra/helm/istio-gateway/files/shortlink-404.html""")
                .isEqualTo(backend);
    }

    @Test
    @DisplayName("VirtualService가 그 파일을 그대로 실어 보낸다 — HTML을 손으로 옮겨 적지 않는다")
    void virtualServiceEmbedsTheFile() throws IOException {
        assertThat(Files.readString(GATEWAY_TEMPLATE, StandardCharsets.UTF_8))
                .contains(".Files.Get \"files/shortlink-404.html\"");
    }
}
