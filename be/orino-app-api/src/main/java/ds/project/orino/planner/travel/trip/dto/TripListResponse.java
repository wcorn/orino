package ds.project.orino.planner.travel.trip.dto;

import java.util.List;

/**
 * 여행 목록 화면(S-02) 응답.
 *
 * @param counts 탭 라벨 뒤 건수. {@code status} 필터와 무관하게 항상 전체 기준으로 센다 —
 *               필터된 목록에서 세면 탭을 옮길 때마다 다른 탭의 숫자가 0이 된다
 * @param trips  필터·정렬이 적용된 목록
 */
public record TripListResponse(TripCounts counts, List<TripSummary> trips) {

    /** 상태별 여행 수. */
    public record TripCounts(long upcoming, long ongoing, long completed) {
    }
}
