package ds.project.orino.planner.travel.trip.dto;

import ds.project.orino.planner.travel.prep.dto.PrepSummary;

import java.time.LocalDate;

/**
 * 워크스페이스 선택 화면(`/select`)의 여행 카드와 여행 홈(S-01)이 함께 쓰는 요약.
 *
 * <p>세 필드가 전부 {@code null}이면 FE는 "여행 만들기" 단일 버튼만 그린다.
 *
 * @param ongoing         진행 중 여행. 보드로 바로 들어가는 용도라 최소 정보만 담는다
 * @param next            다음 예정 여행(진행 중이 있어도 별개로 내려간다)
 * @param recentCompleted 가장 최근에 끝난 여행
 */
public record TravelSummaryResponse(
        OngoingTrip ongoing,
        NextTrip next,
        CompletedTrip recentCompleted
) {

    /**
     * 진행 중 여행 — 탭하면 곧장 보드로 간다.
     *
     * <p>(v2.1) <b>오늘의 도시</b>가 함께 온다. `/select` 카드가 `오늘 오사카 → 교토`를
     * 쓰고 S-01이 `오늘 · 교토`와 그날 타임존·통화를 쓴다 — 둘 다 이 응답 하나로 끝난다.
     */
    public record OngoingTrip(Long id, String title, String boardPath, String prepPath,
                              LocalDate startDate, LocalDate endDate,
                              long activityCount, TripCitySummary cities,
                              PrepSummary prep) {

        public static OngoingTrip of(Long id, String title, LocalDate startDate,
                                     LocalDate endDate, long activityCount,
                                     TripCitySummary cities, PrepSummary prep) {
            return new OngoingTrip(id, title, "/travel/trips/%d/board".formatted(id),
                    "/travel/trips/%d/prep".formatted(id),
                    startDate, endDate, activityCount, cities, prep);
        }
    }

    /**
     * 예정 여행 — D-day 카운트다운 카드.
     *
     * <p>(v2.2) <b>준비 요약이 진행 중 여행뿐 아니라 여기에도 붙는다.</b> 준비는 출발 전에
     * 값을 내는 기능이라, 여행이 시작된 뒤에만 배지가 뜨면 정작 필요한 동안 아무것도
     * 알려주지 않는다(명세 v2.2 §13).
     */
    public record NextTrip(
            Long id,
            String title,
            String destinationName,
            String prepPath,
            LocalDate startDate,
            LocalDate endDate,
            long dDay,
            long activityCount,
            TripCitySummary cities,
            PrepSummary prep
    ) {

        public static NextTrip of(Long id, String title, String destinationName,
                                  LocalDate startDate, LocalDate endDate, long dDay,
                                  long activityCount, TripCitySummary cities,
                                  PrepSummary prep) {
            return new NextTrip(id, title, destinationName,
                    "/travel/trips/%d/prep".formatted(id),
                    startDate, endDate, dDay, activityCount, cities, prep);
        }
    }

    /** 완료 여행 — 돌아보기 카드. */
    public record CompletedTrip(Long id, String title, LocalDate endDate, long activityCount) {
    }
}
