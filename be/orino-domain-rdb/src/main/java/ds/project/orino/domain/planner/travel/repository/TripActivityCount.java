package ds.project.orino.domain.planner.travel.repository;

/**
 * 여행별 일정 수. 목록 화면이 여행마다 COUNT를 날리지 않도록 한 번에 묶어 세는 용도다.
 *
 * @param tripId 여행 id
 * @param count  보관함을 포함한 전체 일정 수
 */
public record TripActivityCount(Long tripId, long count) {
}
