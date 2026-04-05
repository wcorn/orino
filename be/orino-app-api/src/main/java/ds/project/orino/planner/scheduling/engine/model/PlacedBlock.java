package ds.project.orino.planner.scheduling.engine.model;

import ds.project.orino.domain.calendar.entity.BlockType;

import java.time.LocalTime;

/**
 * 엔진이 배치하기로 결정한 블록. 아직 DB에 저장되지 않은 상태.
 */
public record PlacedBlock(
        BlockType blockType,
        long referenceId,
        LocalTime start,
        LocalTime end,
        String title
) {
}
