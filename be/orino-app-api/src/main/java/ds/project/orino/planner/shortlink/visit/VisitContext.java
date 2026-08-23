package ds.project.orino.planner.shortlink.visit;

import java.time.Instant;

/**
 * 방문 한 건을 판정하는 데 필요한 값. <b>요청 스레드에서 뽑아</b> 비동기 기록으로 넘긴다.
 *
 * <p>여기 담긴 원문(User-Agent · Referer)은 <b>판정에만 쓰이고 저장되지 않는다</b>(명세 §8.1).
 * IP는 아예 담지 않는다 — 국가 판정(#1241)이 붙을 때도 판정 직후 버리는 값이라
 * 이 레코드가 그것을 들고 다닐 이유가 없다.
 */
public record VisitContext(String userAgent, String referer, Instant visitedAt) {
}
