package ds.project.orino.planner.shortlink.visit;

import ds.project.orino.domain.planner.shortlink.entity.VisitDevice;

import java.util.Locale;

/**
 * User-Agent 판정. <b>원문은 저장하지 않는다</b> — 여기서 판정하고 결과만 남긴다(명세 §8.1).
 *
 * <p>봇 판정은 <b>완벽하지 않다.</b> 그래서 화면에 그대로 적는다 —
 * {@code 봇·프리뷰 18건은 따로 셉니다 — 통계는 참고치}(명세 §8.2). 여기서 목표는 정확도가
 * 아니라 <b>메신저 프리뷰가 방문 수를 몇 배로 부풀리는 것을 막는 것</b>이다: 카카오톡·슬랙에
 * 링크를 붙이면 사람이 누르기 전에 프리뷰 봇이 먼저 연다.
 */
public final class UserAgents {

    /** 알려진 프리뷰·크롤러. 링크를 붙여넣는 순간 먼저 오는 것들이다. */
    private static final String[] KNOWN_BOTS = {
            "kakaotalk-scrap", "facebookexternalhit", "slackbot", "discordbot",
            "telegrambot", "whatsapp", "twitterbot", "googlebot", "bingbot",
            "yeti", "daumoa", "applebot", "linkedinbot", "embedly", "skypeuripreview"
    };

    /** 이름을 모르는 것들. 새 메신저가 나와도 대개 여기 걸린다. */
    private static final String[] BOT_PATTERNS = {
            "bot", "crawl", "spider", "preview", "scrap", "fetch", "monitor"
    };

    private UserAgents() {
    }

    public static boolean isBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            // UA 없는 요청은 브라우저가 아니다. 사람 방문으로 세면 그쪽이 더 틀린다.
            return true;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        for (String bot : KNOWN_BOTS) {
            if (ua.contains(bot)) {
                return true;
            }
        }
        for (String pattern : BOT_PATTERNS) {
            if (ua.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 기기 구분. 태블릿을 모바일보다 먼저 본다 — 안드로이드 태블릿 UA에는 {@code mobile}이
     * 없고 {@code android}만 있으며, 아이패드는 {@code ipad}와 함께 {@code mobile}을 달고 온다.
     */
    public static VisitDevice deviceOf(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return VisitDevice.UNKNOWN;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("ipad") || ua.contains("tablet")
                || (ua.contains("android") && !ua.contains("mobile"))) {
            return VisitDevice.TABLET;
        }
        if (ua.contains("mobile") || ua.contains("iphone") || ua.contains("android")) {
            return VisitDevice.MOBILE;
        }
        if (ua.contains("windows") || ua.contains("macintosh") || ua.contains("x11")
                || ua.contains("linux")) {
            return VisitDevice.DESKTOP;
        }
        return VisitDevice.UNKNOWN;
    }
}
