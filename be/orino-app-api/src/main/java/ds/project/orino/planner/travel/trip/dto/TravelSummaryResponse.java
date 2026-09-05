package ds.project.orino.planner.travel.trip.dto;

import ds.project.orino.domain.planner.travel.entity.TripStatus;
import ds.project.orino.planner.travel.expense.dto.ExpenseSummary;
import ds.project.orino.planner.travel.prep.dto.PrepSummary;

import java.time.LocalDate;
import java.util.List;

/**
 * 워크스페이스 선택 화면(`/select`)의 여행 카드와 여행 홈(S-01)이 함께 쓰는 요약.
 *
 * <p>앞의 세 필드가 전부 {@code null}이면 FE는 "여행 만들기" 단일 버튼만 그린다.
 *
 * <p>(v2.2) <b>{@code trips[]}가 사이드바 여행 트리와 폴백 화면을 함께 먹인다</b>(API §2.1).
 * 셋만으로는 「진행 중이 둘」이나 「예정이 셋」을 그릴 수 없다 — 앞의 셋은 {@code /select}
 * 카드와 홈이 계속 쓰므로 그대로 두고, 배열을 옆에 더한다.
 *
 * @param ongoing         진행 중 여행. 보드로 바로 들어가는 용도라 최소 정보만 담는다
 * @param next            다음 예정 여행(진행 중이 있어도 별개로 내려간다)
 * @param recentCompleted 가장 최근에 끝난 여행
 * @param trips           진행 중·예정 전부. 진행 중 → 예정, 각각 시작일 오름차순
 * @param completedCount  다녀온 여행 수. 사이드바의 「다녀온 여행 N개」 한 줄이 쓴다
 */
public record TravelSummaryResponse(
        OngoingTrip ongoing,
        NextTrip next,
        CompletedTrip recentCompleted,
        List<SidebarTrip> trips,
        int completedCount
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

    /**
     * 사이드바 여행 트리 한 줄 (v2.2 · API §2.1).
     *
     * <p><b>다녀온 여행은 여기 없다</b> — 사이드바에 늘어놓으면 시간이 갈수록 사이드바가
     * 목록 화면이 된다(D-39). 개수만 {@code completedCount}로 간다.
     *
     * <p>{@code dDay}와 {@code dayNumber}는 <b>동시에 차지 않는다.</b> 진행 중이면
     * 「4일차」, 예정이면 「D-49」 하나만 그릴 자리라, 둘 다 채우면 화면이 무엇을 그릴지
     * 다시 정해야 한다.
     *
     * @param dDay      출발까지 남은 일수. <b>예정일 때만</b> 찬다
     * @param dayNumber 오늘이 며칠째인지(첫날이 1). <b>진행 중일 때만</b> 찬다
     * @param prep      준비 진행률·기한 지남. 항목이 없어도 {@code {0,0,0}}이다({@code null} 아님)
     * @param expense   예산·확정 지출. 예산 미설정이면 {@code budget}이 {@code null}이다
     */
    public record SidebarTrip(
            Long id,
            String title,
            TripStatus status,
            LocalDate startDate,
            LocalDate endDate,
            Long dDay,
            Integer dayNumber,
            PrepSummary prep,
            ExpenseSummary expense
    ) {
    }
}
