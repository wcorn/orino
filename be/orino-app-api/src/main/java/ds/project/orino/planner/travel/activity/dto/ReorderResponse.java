package ds.project.orino.planner.travel.activity.dto;

import ds.project.orino.planner.travel.route.dto.LegResponse;

import java.util.List;

/**
 * 순서 변경 결과. 재계산된 이동시간({@code legs})을 담아 드래그 직후 화면이 다시 조회하지 않고도
 * 이동시간을 갱신할 수 있게 한다 — 드래그는 손을 뗀 순간 결과가 보여야 하는 동작이다.
 *
 * <p>여러 날짜가 한 요청에 섞일 수 있어(날짜 이동) 건드린 날짜의 구간을 모두 담는다.
 * 구간은 일정 id로 식별되므로 날짜가 섞여도 모호하지 않다.
 */
public record ReorderResponse(List<LegResponse> legs) {
}
