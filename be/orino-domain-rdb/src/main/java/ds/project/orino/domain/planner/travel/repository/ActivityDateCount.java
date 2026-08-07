package ds.project.orino.domain.planner.travel.repository;

import java.time.LocalDate;

/**
 * 날짜별 일정 수. 보드의 날짜 탭이 모든 날짜의 건수를 한 번에 필요로 해서, 날짜마다 COUNT를
 * 날리지 않고 한 번에 묶어 센다.
 *
 * @param activityDate 일정 날짜(보관함은 이 집계에서 제외한다)
 * @param count        그 날짜의 일정 수
 */
public record ActivityDateCount(LocalDate activityDate, long count) {
}
