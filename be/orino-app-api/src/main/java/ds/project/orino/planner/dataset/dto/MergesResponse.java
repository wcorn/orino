package ds.project.orino.planner.dataset.dto;

import java.util.List;

/**
 * 한 dataset의 병합 전체. 세로 병합은 앵커 행이 화면 밖에 있어도 덮인 행을 그려야 하므로,
 * 병합은 페이지가 아니라 dataset 단위로 통째 내려간다(병합은 sparse라 대개 적다).
 */
public record MergesResponse(
        List<MergeView> merges
) {
}
