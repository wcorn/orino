package ds.project.orino.planner.shortlink.og;

/**
 * 파싱 결과. 둘 다 없을 수 있다 — 프리뷰는 확인용이고 발급을 막지 않는다(명세 §4.4).
 */
public record OgPreviewResult(String title, String imageUrl) {

    public static OgPreviewResult empty() {
        return new OgPreviewResult(null, null);
    }

    public boolean isEmpty() {
        return title == null && imageUrl == null;
    }
}
