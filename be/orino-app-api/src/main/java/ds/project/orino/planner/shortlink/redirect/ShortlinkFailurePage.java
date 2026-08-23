package ds.project.orino.planner.shortlink.redirect;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * 실패 화면 한 장(명세 §7).
 *
 * <p>이 화면은 <b>orino의 유일한 공개 표면</b>이고 기준은 「정보를 주지 않는 것」이다. 없음 ·
 * 꺼짐 · 만료 · 삭제를 구분하지 않으며, 그래서 실패 경로가 모두 이 한 곳으로 모인다 —
 * 분기가 늘면 언젠가 응답이 갈리고, <b>갈린 차이 자체가 정보</b>가 된다.
 *
 * <p>FE 번들을 거치지 않는 서버 렌더 정적 HTML이다(명세 §6.1). {@code /links}가 깨져도,
 * FE 배포가 실패해도 이 화면은 뜬다.
 *
 * <p>같은 HTML이 게이트웨이에도 있다({@code infra/helm/istio-gateway/files/shortlink-404.html}).
 * 슬러그 문자셋 밖 요청은 BE에 닿기 전에 Envoy가 직접 404를 내기 때문이다(결정 기록 D-7).
 * 두 파일이 어긋나면 그 차이가 곧 "이 슬러그는 형식이 맞다"는 신호가 되므로,
 * {@code ShortlinkFailurePageParityTest}가 바이트 단위로 같은지 고정한다.
 */
@Component
public class ShortlinkFailurePage {

    private static final String RESOURCE_PATH = "shortlink/404.html";

    private final String html;

    public ShortlinkFailurePage() {
        this.html = load();
    }

    public String html() {
        return html;
    }

    private static String load() {
        try {
            return new ClassPathResource(RESOURCE_PATH).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 이 화면 없이는 공개 표면이 스택트레이스를 노출하게 된다. 기동을 멈추는 편이 낫다.
            throw new UncheckedIOException("실패 화면 리소스를 읽지 못했습니다: " + RESOURCE_PATH, e);
        }
    }
}
