package ds.project.orino.planner.dataset.dto;

/**
 * 한 앵커 셀의 병합 범위. {@code RowView.merges}에 앵커 열 key로 담기며, 병합 있는 셀만 들어간다
 * ({@code formulas}·{@code styles}와 같은 sparse 맵).
 *
 * <p>{@code rowSpan}·{@code colSpan}은 각 &ge;1이고 {@code (1,1)}은 병합이 아니라 담기지 않는다.
 * 슬라이스 1(가로 병합)에선 {@code rowSpan}이 항상 1이다.
 */
public record MergeSpec(
        int rowSpan,
        int colSpan
) {
}
