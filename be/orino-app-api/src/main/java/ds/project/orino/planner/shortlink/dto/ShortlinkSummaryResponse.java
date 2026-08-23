package ds.project.orino.planner.shortlink.dto;

/**
 * {@code /select} 링크 카드의 메타 줄. 실패하면 FE는 메타 줄 자체를 그리지 않는다(화면 §2).
 *
 * @param total          살아 있는 링크 수
 * @param visitsThisWeek 이번 주 방문. <b>통계(#1240) 전까지는 0이다</b>
 * @param baseUrl        공개 base URL(`https://s.orino.dev`). 발급 모달이 <b>만들기 전에</b>
 *                       「17자」를 보여 주려면 도메인을 알아야 하는데, 아직 링크가 하나도 없으면
 *                       기존 {@code shortUrl}에서 얻을 수가 없다. 그렇다고 FE에 도메인을
 *                       하드코딩하면 환경이 갈릴 때 두 곳이 어긋난다 — 그래서 서버가 알려 준다
 */
public record ShortlinkSummaryResponse(long total, long visitsThisWeek, String baseUrl) {
}
