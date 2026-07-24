package ds.project.orino.planner.lifelog.flow.dto;

import ds.project.orino.domain.planner.lifelog.entity.FlowStatus;
import ds.project.orino.planner.lifelog.moment.dto.MomentCard;

import java.time.Instant;
import java.util.List;

/**
 * 흐름 상세. 담긴 기록을 순서(sort_order 우선, 동률이면 occurred_at)대로 카드로 담는다.
 */
public record FlowDetail(
        Long id,
        String title,
        String description,
        String coverUrl,
        Instant startedAt,
        Instant endedAt,
        FlowStatus status,
        List<MomentCard> moments
) {
}
