package ds.project.orino.planner.travel.day.service;

import ds.project.orino.domain.planner.travel.entity.TravelPlace;
import ds.project.orino.domain.planner.travel.entity.TripDay;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 도시가 바뀌는 날 — <b>그 하루는 두 도시에 속한다</b>(D-25).
 *
 * <p>오사카 구간이 10.27에 끝나고 교토 구간이 10.28에 시작하면, 10.28은 오전엔 오사카고
 * 오후엔 교토다. 그날의 기준 도시는 <b>도착한 도시 하나</b>로 두되(시계가 둘이면 "오늘"의
 * 정의가 무너진다), 어디에 있었는지를 묻는 판정은 <b>떠나온 도시도 함께</b> 본다.
 *
 * <p><b>저장하지 않는다.</b> 떠나온 도시는 직전 날짜의 기준 도시일 뿐이라 {@code trip_day}만
 * 있으면 매번 나온다. 구간을 박(night) 단위로 저장해 경계일을 공유시키는 안도 검토했지만,
 * 그러면 SSOT가 날짜에서 구간으로 옮겨 가고({@link LegDeriver} 참조) <b>당일치기를 표현하지
 * 못한다</b> — 도쿄에 자면서 닛코에 다녀오는 날은 자는 도시가 도쿄라 닛코가 사라진다. 박은
 * {@code trip_stay}가 이미 정확히 안다.
 */
public final class TransitionDays {

    private TransitionDays() {
    }

    /**
     * 도시가 바뀌는 날 → <b>떠나온 도시</b>의 장소 id.
     *
     * <p>바뀌지 않는 날은 키 자체가 없다 — 그래서 {@code containsKey}가 곧 "도시가 바뀌었나"다.
     * 첫날은 비교할 앞 날짜가 없으므로 바뀐 것이 아니다.
     *
     * @param days <b>날짜 오름차순</b>으로 정렬된 여행 날짜. 순서가 어긋나면 판정도 어긋난다
     */
    public static Map<LocalDate, Long> departedByDate(Iterable<TripDay> days) {
        Map<LocalDate, Long> departed = new LinkedHashMap<>();
        TripDay previous = null;
        for (TripDay day : days) {
            if (previous != null && !previous.getBasePlaceId().equals(day.getBasePlaceId())) {
                departed.put(day.getDayDate(), previous.getBasePlaceId());
            }
            previous = day;
        }
        return departed;
    }

    /**
     * 그날 <b>있어도 되는 도시</b>의 식별자들. 일정이 이 중 어디에도 속하지 않을 때만 다른
     * 도시로 본다.
     *
     * <p>식별자가 없는 도시(직접 입력한 도시)는 넣지 않는다. 전부 빠져 집합이 비면 판정 자체를
     * 하지 않는다 — 모르는 것을 "다르다"로 답하면 멀쩡한 일정에 경고가 붙는다(D-23).
     *
     * @param cities 도착한 도시와 떠나온 도시. {@code null}은 건너뛴다
     */
    public static Set<String> cityRefsOf(TravelPlace... cities) {
        Set<String> refs = new LinkedHashSet<>();
        for (TravelPlace city : cities) {
            if (city != null && city.getCityPlaceRef() != null) {
                refs.add(city.getCityPlaceRef());
            }
        }
        return refs;
    }
}
