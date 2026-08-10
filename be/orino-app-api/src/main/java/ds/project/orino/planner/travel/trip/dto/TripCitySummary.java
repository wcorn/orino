package ds.project.orino.planner.travel.trip.dto;

import ds.project.orino.domain.planner.travel.entity.TravelPlace;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 여행을 <b>고르는 자리</b>가 쓰는 도시 요약 — `/select` 카드 · S-01 홈 · S-02 목록.
 *
 * <p>세 화면이 같은 값을 다르게 줄여 쓴다(도시 나열과 오늘의 도시). 줄이는 규칙은 화면이
 * 갖고, 서버는 <b>줄이기 전의 사실</b>만 준다.
 *
 * <p>이 값들은 목록이 <b>이미 읽어 둔 날짜 지도</b>에서 나온다. 여행마다
 * {@code /city-legs}를 부르면 N+1이 되지만, 목록은 처음부터 모든 여행의 날짜별 기준 도시를
 * 한 번에 받아 상태·D-day를 판정하고 있어 <b>추가 조회가 0이다.</b>
 *
 * @param names        구간 순서의 도시 이름. 연속으로 같은 도시는 이미 한 번으로 접혀 있다
 *                     (도쿄 → 닛코 → 도쿄는 세 개다 — 사이가 끊기면 다른 구간이다)
 * @param count        서로 다른 도시 수. 같은 도시를 다시 방문해도 하나로 센다
 * @param today        오늘의 기준 도시 이름. 진행 중이 아니면 null
 * @param movedFrom    오늘 도시가 바뀌었다면 <b>어제의</b> 도시. 아니면 null
 * @param todayDayIndex 오늘이 며칠째인지(1부터). 진행 중이 아니면 null
 * @param todayTimezone 오늘 도시의 타임존. 진행 중이 아니면 null
 * @param todayCurrency 오늘 도시의 통화. 진행 중이 아니면 null
 */
public record TripCitySummary(
        List<String> names,
        int count,
        String today,
        String movedFrom,
        Integer todayDayIndex,
        String todayTimezone,
        String todayCurrency
) {

    private static final TripCitySummary EMPTY =
            new TripCitySummary(List.of(), 0, null, null, null, null, null);

    public static TripCitySummary empty() {
        return EMPTY;
    }

    /**
     * 날짜별 기준 도시를 도시 나열로 접는다.
     *
     * @param today 오늘 날짜. 진행 중이 아니면 null을 주면 오늘 관련 값이 전부 비워진다
     */
    public static TripCitySummary of(LocalDate startDate, LocalDate endDate,
                                     Map<LocalDate, TravelPlace> byDate, LocalDate today) {
        List<String> names = new ArrayList<>();
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        Long previousId = null;
        String previousName = null;
        String todayName = null;
        String movedFrom = null;
        Integer dayIndex = null;
        String timezone = null;
        String currency = null;

        int index = 0;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            index++;
            TravelPlace city = byDate.get(date);
            if (city == null) {
                continue;
            }
            String name = nameOf(city);
            if (!city.getId().equals(previousId)) {
                names.add(name);
                distinct.add(city.getId());
            }
            if (date.equals(today)) {
                todayName = name;
                // 어제와 다르면 오늘이 옮기는 날이다 — 화면이 `오사카 → 교토`로 쓴다.
                movedFrom = city.getId().equals(previousId) ? null : previousName;
                dayIndex = index;
                timezone = city.getTimezone();
                currency = city.getCurrency();
            }
            previousId = city.getId();
            previousName = name;
        }
        return new TripCitySummary(List.copyOf(names), distinct.size(),
                todayName, movedFrom, dayIndex, timezone, currency);
    }

    private static String nameOf(TravelPlace city) {
        return city.getCityName() != null ? city.getCityName() : city.getName();
    }
}
