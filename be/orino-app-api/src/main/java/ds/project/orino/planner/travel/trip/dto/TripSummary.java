package ds.project.orino.planner.travel.trip.dto;

import ds.project.orino.domain.planner.travel.entity.TripStatus;

import java.time.LocalDate;

/**
 * 여행 목록 카드 하나.
 *
 * @param status        저장된 값이 아니라 여행 타임존의 오늘로 파생한 상태
 * @param dDay          시작일까지 남은 일수. 시작 당일 0, 이미 시작했으면 음수
 * @param activityCount 보관함을 포함한 전체 일정 수
 * @param cities        (v2.1) 구간 순서의 도시와 오늘의 도시. 카드가 이걸 줄여 쓴다
 */
public record TripSummary(
        Long id,
        String title,
        String destinationName,
        LocalDate startDate,
        LocalDate endDate,
        TripStatus status,
        long dDay,
        long activityCount,
        TripCitySummary cities
) {
}
