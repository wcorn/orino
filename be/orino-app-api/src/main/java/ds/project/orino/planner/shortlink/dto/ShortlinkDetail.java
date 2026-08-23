package ds.project.orino.planner.shortlink.dto;

import java.time.Instant;
import java.util.List;

/**
 * 상세 응답 = {@link LinkSummary} + 발급 시각·만료·OG·<b>목적지 교체 이력</b>.
 *
 * <p>이력은 시간 역순이고 <b>비어 있는 경우가 없다</b> — 최초 발급이 그 마지막 줄이다(명세 §5.1).
 *
 * @param og OG 프리뷰. #1243 전까지는 항상 null이다
 */
public record ShortlinkDetail(
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
        Instant createdAt,
        Instant expiresAt,
        OgPreview og,
        List<TargetHistoryEntry> targetHistory
) {

    public static ShortlinkDetail of(LinkSummary summary, Instant createdAt, Instant expiresAt,
                                     OgPreview og, List<TargetHistoryEntry> targetHistory) {
        return new ShortlinkDetail(summary.slug(), summary.shortUrl(), summary.targetUrl(),
                summary.memo(), summary.tags(), summary.custom(), summary.favorite(),
                summary.state(), summary.hasPassword(), summary.visitCount(),
                summary.lastVisitedAt(), createdAt, expiresAt, og, targetHistory);
    }
}
