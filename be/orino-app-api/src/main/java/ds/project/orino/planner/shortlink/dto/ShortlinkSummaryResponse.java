package ds.project.orino.planner.shortlink.dto;

/**
 * {@code /select} 링크 카드의 메타 줄. 실패하면 FE는 메타 줄 자체를 그리지 않는다(화면 §2).
 *
 * @param total          살아 있는 링크 수
 * @param visitsThisWeek 이번 주 방문. <b>통계(#1240) 전까지는 0이다</b>
 */
public record ShortlinkSummaryResponse(long total, long visitsThisWeek) {
}
