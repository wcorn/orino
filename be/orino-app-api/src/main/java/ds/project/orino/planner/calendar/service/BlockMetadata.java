package ds.project.orino.planner.calendar.service;

/**
 * 블록 표시용 메타정보. title/category는 blockType+referenceId 기반으로
 * 각 도메인 엔티티에서 조회한다.
 */
public record BlockMetadata(String title, String categoryName,
                            String categoryColor) {

    private static final String UNKNOWN_TITLE = "(알 수 없음)";
    private static final String DEFAULT_COLOR = "#888888";

    public static BlockMetadata unknown() {
        return new BlockMetadata(UNKNOWN_TITLE, null, DEFAULT_COLOR);
    }
}
