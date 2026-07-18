package ds.project.orino.planner.dataset.dto;

import java.util.List;

/**
 * 한 dataset의 기본이 아닌 행 높이 전체. 세로 병합은 앵커 행이 화면 밖이어도 덮인 영역의 높이를
 * 알아야 하므로, 병합처럼 페이지가 아니라 dataset 단위로 통째 내려간다(대개 sparse).
 */
public record RowHeightsResponse(
        List<RowHeightView> heights
) {
}
