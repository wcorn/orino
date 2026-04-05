package ds.project.orino.planner.scheduling.engine.model;

import java.util.List;

/**
 * AvailabilityCalculator의 결과.
 * 자유 시간 블록과, 고정 일정/루틴으로 미리 배치된 블록을 함께 담는다.
 */
public record AvailabilityResult(
        List<TimeSlot> freeSlots,
        List<PlacedBlock> preplacedBlocks
) {
}
