package ds.project.orino.planner.google.routine.dto;

import java.util.List;

/**
 * 루틴 시리즈 목록 응답. 관리 화면이 시리즈 단위로 렌더한다.
 */
public record RoutineListResponse(List<RoutineSeriesSummary> routines) {
}
