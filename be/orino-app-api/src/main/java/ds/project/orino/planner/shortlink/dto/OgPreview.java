package ds.project.orino.planner.shortlink.dto;

/**
 * OG 프리뷰 결과. 프리뷰는 <b>확인용이고 발급을 막지 않는다</b>(명세 §4.4) —
 * 채우는 쪽(#1243)은 SSRF 방어와 같은 PR에서만 켠다.
 */
public record OgPreview(String title, String imageUrl) {
}
