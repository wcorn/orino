package ds.project.orino.planner.travel.place.dto;

/**
 * 도시 이름 새로고침 결과(#1375).
 *
 * <p><b>남은 개수를 함께 준다.</b> 한 번에 처리하는 수를 제한했으므로, 화면이 「몇 번 더
 * 눌러야 하나」를 말할 수 있어야 한다 — 끝났는지 모르면 사용자는 될 때까지 계속 누르고
 * 그만큼 유료 호출이 나간다.
 *
 * @param refreshed 이번에 값을 채운 장소 수. 구글이 광역 행정구역을 안 준 장소는 안 센다
 * @param remaining 아직 못 채운 장소 수. 0이면 끝이다
 */
public record CityRefreshResponse(int refreshed, long remaining) {
}
