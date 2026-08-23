package ds.project.orino.planner.shortlink.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Locale;

/**
 * 단축 URL 공개 주소 설정.
 *
 * <p>짧은 주소 문자열은 <b>서버가 조립해 내려준다</b>(화면 설계 §0). 행마다 도메인을 저장하지도
 * 않고(데이터 모델 §3), FE에 하드코딩하지도 않는다 — 환경마다 호스트가 달라지면 두 곳이 어긋난다.
 *
 * <p>이 호스트는 자기참조 판정(명세 §4.3)에도 쓰인다. 자기 자신을 가리키는 링크는 루프다.
 *
 * @param baseUrl 공개 base URL (예: {@code https://s.orino.dev}). 끝의 {@code /}는 무시한다
 */
@ConfigurationProperties(prefix = "shortlink")
public record ShortlinkProperties(String baseUrl) {

    public ShortlinkProperties {
        baseUrl = baseUrl == null ? "" : baseUrl.strip();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
    }

    /** {@code https://s.orino.dev/ab3k9} — 명세 §3의 17자가 나오는 자리. */
    public String shortUrl(String slug) {
        return baseUrl + "/" + slug;
    }

    /** 자기참조 판정용 호스트(소문자). 설정이 URL 꼴이 아니면 빈 문자열이다. */
    public String host() {
        try {
            String host = URI.create(baseUrl).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
