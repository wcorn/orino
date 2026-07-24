package ds.project.orino.planner.lifelog.flow.dto;

import ds.project.orino.domain.planner.lifelog.entity.FlowStatus;

import java.time.Instant;

/**
 * 흐름 목록 카드. 기간은 담긴 기록에서 유도해 저장된 값, 커버는 없으면 담긴 첫 사진.
 */
public record FlowSummary(
        Long id,
        String title,
        String description,
        String coverUrl,
        Instant startedAt,
        Instant endedAt,
        long momentCount,
        FlowStatus status
) {
}
