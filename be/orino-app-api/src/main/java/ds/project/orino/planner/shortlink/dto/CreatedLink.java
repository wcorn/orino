package ds.project.orino.planner.shortlink.dto;

import java.time.Instant;
import java.util.List;

/**
 * 발급 응답 = {@link LinkSummary} + {@code qrPayload}. 발급 직후 화면이 QR을 바로 그린다.
 *
 * <p>{@code qrPayload}는 지금 {@code shortUrl}과 같은 값이지만 별도 필드로 둔다 —
 * QR에 무엇을 담을지는 주소 표기와 따로 움직일 수 있고, 화면은 이미 둘을 다른 자리에 쓴다.
 */
public record CreatedLink(
        String slug,
        String shortUrl,
        String targetUrl,
        String memo,
        List<String> tags,
        boolean custom,
        boolean favorite,
        LinkState state,
        boolean hasPassword,
        long visitCount,
        Instant lastVisitedAt,
        String qrPayload
) {

    public static CreatedLink of(LinkSummary summary) {
        return new CreatedLink(summary.slug(), summary.shortUrl(), summary.targetUrl(),
                summary.memo(), summary.tags(), summary.custom(), summary.favorite(),
                summary.state(), summary.hasPassword(), summary.visitCount(),
                summary.lastVisitedAt(), summary.shortUrl());
    }
}
