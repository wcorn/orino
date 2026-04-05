package ds.project.orino.planner.scheduling.engine.model;

import java.util.List;

/**
 * BlockPlacer가 리턴하는 결과. 배치된 블록과 이월 항목을 함께 담는다.
 */
public record PlacementResult(
        List<PlacedBlock> placedBlocks,
        List<OverflowItem> overflows
) {
}
