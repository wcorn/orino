package ds.project.orino.planner.shortlink.dto;

import java.util.List;

/**
 * 목록 응답. 화면이 즐겨찾기 섹션과 최근 발급 섹션을 따로 그리므로 서버가 나눠서 준다(화면 §3.2).
 *
 * <p><b>{@code recent}에는 즐겨찾기가 들어가지 않는다</b> — 두 섹션에 같은 카드가 두 번
 * 나오면 목록의 개수와 눈에 보이는 행 수가 어긋난다. 총계는 {@code counts}가 답한다.
 *
 * @param counts 상태 칩 숫자. <b>상태 필터를 적용하기 전</b>의 검색·태그 결과를 기준으로 센다 —
 *               상태로 걸러진 뒤의 숫자를 주면 다른 칩의 숫자를 알 수 없다
 */
public record ShortlinkListResponse(
        Counts counts,
        List<LinkSummary> favorites,
        List<LinkSummary> recent
) {

    /** {@code inactive}는 꺼짐 + 만료다 — 화면 칩과 1:1이다(API 설계 §2). */
    public record Counts(long all, long active, long inactive) {
    }
}
