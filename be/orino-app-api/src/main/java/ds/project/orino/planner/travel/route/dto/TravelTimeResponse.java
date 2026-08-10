package ds.project.orino.planner.travel.route.dto;

import ds.project.orino.planner.travel.route.client.TravelMode;

/**
 * 연속한 두 일정 사이 이동(§4.4). 보드 응답에 실려 일정 리스트에 항상 표시된다.
 *
 * @param fromActivityId  출발 일정
 * @param toActivityId    도착 일정. 사이에 장소 없는 일정이 끼면 건너뛴 결과다
 * @param durationMinutes 소요 시간(분). {@code fallback}이면 null — 거리만 안다
 * @param distanceM       거리(m). 경로 거리이고, {@code fallback}이면 직선거리다
 * @param fallback        Routes를 못 얻어 직선거리로 대체했다. FE는 {@code 약 N.Nkm}로 보여준다
 */
public record TravelTimeResponse(
        Long fromActivityId,
        Long toActivityId,
        TravelMode mode,
        Integer durationMinutes,
        int distanceM,
        boolean fallback
) {
}
