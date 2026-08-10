package ds.project.orino.planner.travel.day.service;

import ds.project.orino.domain.planner.travel.entity.TripDay;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 날짜에서 구간을 <b>파생</b>한다 — 연속된 같은 기준 도시 날짜를 하나로 묶는다.
 * {@link LegExpander}(구간 → 날짜)의 반대 방향이다.
 *
 * <p><b>구간을 저장하지 않는 이유</b>(D-21): 저장하면 날짜와 구간이 어긋날 수 있는 상태가 두 개
 * 생긴다. 하루의 기준 도시를 바꿨는데 구간 테이블이 그대로면, 화면 두 곳이 서로 다른 답을
 * 보여준다.
 *
 * <p>하루만 바꿔서 구간이 셋으로 쪼개지는 것(도쿄/닛코/도쿄)은 <b>정상</b>이다. 같은 도시라도
 * 사이에 다른 도시가 끼면 다른 구간이다 — "언제 어디에 있었나"가 구간의 뜻이기 때문이다.
 */
public final class LegDeriver {

    private LegDeriver() {
    }

    /**
     * @param days <b>날짜 오름차순</b>으로 정렬된 여행 날짜. 순서가 어긋나면 구간도 어긋난다
     */
    public static List<DerivedLeg> derive(List<TripDay> days) {
        List<DerivedLeg> legs = new ArrayList<>();
        for (TripDay day : days) {
            DerivedLeg last = legs.isEmpty() ? null : legs.getLast();
            if (last != null && last.basePlaceId().equals(day.getBasePlaceId())) {
                legs.set(legs.size() - 1, last.extendedTo(day.getDayDate()));
            } else {
                legs.add(new DerivedLeg(legs.size() + 1, day.getBasePlaceId(),
                        day.getDayDate(), day.getDayDate()));
            }
        }
        return legs;
    }

    /**
     * 파생된 구간 하나.
     *
     * @param legIndex 1부터. 같은 도시를 다시 방문하면 번호가 다르다(구간이 다르다)
     */
    public record DerivedLeg(int legIndex, Long basePlaceId,
                             LocalDate startDate, LocalDate endDate) {

        private DerivedLeg extendedTo(LocalDate date) {
            return new DerivedLeg(legIndex, basePlaceId, startDate, date);
        }

        /** 머무는 일수(당일 포함). 구간 편집기가 이 값을 스테퍼 초기값으로 쓴다. */
        public int days() {
            return (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        }
    }
}
