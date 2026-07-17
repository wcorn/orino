package ds.project.orino.planner.dataset.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 한 셀의 서식. {@code RowView.styles}에 열 key로 담기며, 서식 있는 셀만 들어간다
 * ({@code formulas}와 같은 sparse 맵).
 *
 * <p>{@code bg}는 팔레트 토큰명(hex 아님)이라 다크모드는 클라이언트가 토큰으로 해결한다.
 * null 필드는 직렬화에서 빠진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CellStyle(
        String bg,
        String align
) {
    /** 허용 배경색 토큰. 디자인 시스템의 셀 하이라이트 팔레트와 맞춘다. */
    public static final java.util.Set<String> ALLOWED_BG =
            java.util.Set.of("red", "orange", "yellow", "green", "blue", "purple");
    /** 허용 정렬 값. */
    public static final java.util.Set<String> ALLOWED_ALIGN =
            java.util.Set.of("left", "center", "right");
}
