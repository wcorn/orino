package ds.project.orino.planner.shortlink.visit;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * 리퍼러에서 <b>도메인만</b> 뽑는다.
 *
 * <p><b>자르는 시점이 저장 직전이 아니라 여기다.</b> 원시 테이블에도 전체 URL이 들어가지
 * 않는다(명세 §8.1) — 경로와 쿼리에는 사용자가 어디서 무엇을 보고 있었는지가 그대로 들어 있고,
 * 그건 "그 링크가 쓰였나"를 가늠하는 데 필요하지 않다.
 */
public final class Referrers {

    private static final int MAX_LENGTH = 255;

    private Referrers() {
    }

    /**
     * @return 소문자 호스트. 리퍼러가 없거나 호스트를 읽을 수 없으면 null
     */
    public static String domainOf(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            String host = new URI(referer.strip()).getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            String domain = host.toLowerCase(Locale.ROOT);
            return domain.length() > MAX_LENGTH ? domain.substring(0, MAX_LENGTH) : domain;
        } catch (URISyntaxException e) {
            // 읽을 수 없는 리퍼러는 버린다. 원문을 대신 저장하지 않는다.
            return null;
        }
    }
}
