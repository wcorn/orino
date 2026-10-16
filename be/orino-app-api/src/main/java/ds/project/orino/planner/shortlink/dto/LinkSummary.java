package ds.project.orino.planner.shortlink.dto;

import java.time.Instant;
import java.util.List;

/**
 * 목록 카드 1건. 목록·발급·상세가 같은 뼈대를 쓴다(API 설계 §2).
 *
 * @param shortUrl      서버가 조립한 짧은 주소 전체 문자열. FE는 도메인을 알 필요가 없다
 * @param title         사용자가 붙인 이름. null이면 화면은 짧은 주소를 제목 자리에 둔다
 * @param memo          화면에서는 「설명」이다. 필드 이름은 발급 때부터 memo라 그대로 둔다
 * @param custom        사용자가 슬러그를 직접 지었는지(「커스텀」 배지)
 * @param state         파생값. 화면은 이 값 하나만 보고 배지를 정한다
 * @param visitCount    사람 방문만. <b>통계(#1240) 전까지는 항상 0이다</b> —
 *                      필드를 미리 내려 FE 계약을 고정하고 값만 나중에 채운다
 * @param lastVisitedAt 마지막 사람 방문. 통계 전까지는 null
 */
public record LinkSummary(
        String slug,
        String shortUrl,
        String targetUrl,
        String title,
        String memo,
        List<String> tags,
        boolean custom,
        boolean favorite,
        LinkState state,
        boolean hasPassword,
        long visitCount,
        Instant lastVisitedAt
) {
}
