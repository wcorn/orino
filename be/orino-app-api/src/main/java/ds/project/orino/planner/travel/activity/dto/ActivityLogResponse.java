package ds.project.orino.planner.travel.activity.dto;

import ds.project.orino.domain.planner.travel.entity.TripActivityLog;

import java.time.Instant;

/**
 * 일정의 사후 기록. 기록이 없으면 이 객체 자체가 null이다 — 빈 값으로 채운 껍데기를
 * 내려주면 화면이 "기록 있음"과 "아직 없음"을 구분하지 못한다.
 *
 * @param updatedAt 자동 저장이라 언제 저장됐는지 화면이 보여줄 수 있어야 한다
 */
public record ActivityLogResponse(
        Integer rating,
        String memo,
        Instant updatedAt
) {

    public static ActivityLogResponse from(TripActivityLog log) {
        return new ActivityLogResponse(log.getRating(), log.getMemo(), log.getUpdatedAt());
    }
}
