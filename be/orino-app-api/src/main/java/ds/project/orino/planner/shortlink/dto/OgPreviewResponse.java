package ds.project.orino.planner.shortlink.dto;

/**
 * OG 프리뷰 응답. <b>실패에 이유를 담지 않는다</b> — {@code {"ok": false}} 하나다.
 * "연결 거부"와 "타임아웃"을 구분해 주면 그게 곧 내부망 포트 스캐너다(아키텍처 §5).
 */
public record OgPreviewResponse(boolean ok, String title, String imageUrl) {

    public static OgPreviewResponse failed() {
        return new OgPreviewResponse(false, null, null);
    }
}
