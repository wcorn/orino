package ds.project.orino.planner.lifelog.moment.dto;

import ds.project.orino.domain.planner.lifelog.entity.Mood;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 피드·상세 공통 응답 카드. 사진·태그·소속 흐름을 함께 담는다.
 */
public record MomentCard(
        Long id,
        Instant occurredAt,
        String body,
        Mood mood,
        BigDecimal lat,
        BigDecimal lng,
        String placeName,
        List<String> tags,
        List<MomentPhotoResponse> photos,
        List<FlowRef> flows,
        Instant createdAt
) {
}
