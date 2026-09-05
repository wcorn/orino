package ds.project.orino.planner.travel.prep.dto;

/**
 * 여행 전체의 진행률과 기한 지남 개수.
 *
 * <p><b>{@code overdueCount}는 전체 기준 하나다.</b> 사이드바 배지와 화면 상단 경고가 같은
 * 값을 읽어야 한다 — 각자 세면 배지에 1이 떠 있는데 화면에는 아무 줄도 빨갛지 않은 상태가
 * 생기고, 사용자는 무엇을 눌러야 배지가 사라지는지 알 수 없다.
 */
public record PrepSummary(
        int total,
        int done,
        int overdueCount
) {
}
