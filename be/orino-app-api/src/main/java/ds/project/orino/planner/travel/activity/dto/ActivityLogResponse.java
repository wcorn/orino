package ds.project.orino.planner.travel.activity.dto;

import ds.project.orino.domain.planner.travel.entity.TripActivityLog;
import ds.project.orino.planner.travel.photo.dto.PhotoResponse;

import java.time.Instant;
import java.util.List;

/**
 * 일정의 사후 기록. 기록이 없으면 이 객체 자체가 null이다 — 빈 값으로 채운 껍데기를
 * 내려주면 화면이 "기록 있음"과 "아직 없음"을 구분하지 못한다.
 *
 * @param photos    업로드 순서대로. 사진이 없으면 빈 배열이다(null 아님)
 * @param updatedAt 자동 저장이라 언제 저장됐는지 화면이 보여줄 수 있어야 한다
 */
public record ActivityLogResponse(
        Integer rating,
        String memo,
        List<PhotoResponse> photos,
        Instant updatedAt
) {

    public static ActivityLogResponse from(TripActivityLog log, List<PhotoResponse> photos) {
        return new ActivityLogResponse(log.getRating(), log.getMemo(),
                photos == null ? List.of() : photos, log.getUpdatedAt());
    }
}
