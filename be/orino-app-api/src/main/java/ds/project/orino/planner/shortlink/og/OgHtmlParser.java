package ds.project.orino.planner.shortlink.og;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OG 태그만 꺼낸다. <b>HTML 파서를 들이지 않는다</b> — 필요한 것이 {@code <head>}의 메타 두
 * 줄뿐이고, 그것 때문에 의존성을 하나 더 늘리면 이 코드의 공격 표면만 커진다.
 *
 * <p>대신 <b>못 읽는 경우를 정상으로 다룬다</b>. 프리뷰는 확인용이고 발급을 막지 않으므로
 * (명세 §4.4), 파싱이 실패하면 빈 값이 될 뿐이다.
 */
public final class OgHtmlParser {

    /** {@code <meta property="og:title" content="...">} — 속성 순서가 뒤집힌 것도 있다. */
    private static final Pattern META = Pattern.compile(
            "<meta\\s+[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile(
            "(property|name|content)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE = Pattern.compile(
            "<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final int MAX_TITLE = 255;
    private static final int MAX_IMAGE_URL = 1024;

    private OgHtmlParser() {
    }

    public static OgPreviewResult parse(String html) {
        String title = truncate(unescape(metaContent(html, "og:title")), MAX_TITLE);
        if (title == null) {
            // og:title이 없으면 <title>로 대신한다. 사용자에게는 둘 다 "그 페이지 이름"이다.
            title = truncate(unescape(documentTitle(html)), MAX_TITLE);
        }
        String image = truncate(unescape(metaContent(html, "og:image")), MAX_IMAGE_URL);
        return new OgPreviewResult(title, image);
    }

    private static String metaContent(String html, String key) {
        Matcher tags = META.matcher(html);
        while (tags.find()) {
            String tag = tags.group();
            String property = null;
            String content = null;
            Matcher attributes = ATTR.matcher(tag);
            while (attributes.find()) {
                String name = attributes.group(1).toLowerCase(Locale.ROOT);
                String value = attributes.group(3) != null
                        ? attributes.group(3) : attributes.group(4);
                if (name.equals("content")) {
                    content = value;
                } else {
                    property = value;
                }
            }
            if (property != null && property.equalsIgnoreCase(key) && hasText(content)) {
                return content.strip();
            }
        }
        return null;
    }

    private static String documentTitle(String html) {
        Matcher matcher = TITLE.matcher(html);
        return matcher.find() && hasText(matcher.group(1)) ? matcher.group(1).strip() : null;
    }

    /** 흔한 엔티티만 되돌린다. 프리뷰 문구라 완벽할 필요가 없다. */
    private static String unescape(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ");
    }

    private static String truncate(String value, int max) {
        if (!hasText(value)) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
