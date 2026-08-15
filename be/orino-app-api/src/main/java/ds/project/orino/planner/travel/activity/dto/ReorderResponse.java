package ds.project.orino.planner.travel.activity.dto;

import ds.project.orino.planner.travel.move.dto.MoveResponse;

import java.util.List;

/**
 * 순서 변경 결과. 다시 이어진 구간의 이동({@code moves})을 담아 드래그 직후 화면이 다시
 * 조회하지 않고도 갱신할 수 있게 한다 — 드래그는 손을 뗀 순간 결과가 보여야 하는 동작이다.
 *
 * <p>순서가 바뀌면 <b>어느 두 장소가 이어지는지</b>가 바뀐다. 저장된 이동 자체는 장소 쌍에
 * 붙어 있어 그대로지만, 어떤 이동이 어느 자리에 오는지는 달라진다.
 *
 * <p>여러 날짜가 한 요청에 섞일 수 있어(날짜 이동) 건드린 날짜의 이동을 모두 담는다.
 * 이동은 일정 id로 식별되므로 날짜가 섞여도 모호하지 않다.
 */
public record ReorderResponse(List<MoveResponse> moves) {
}
