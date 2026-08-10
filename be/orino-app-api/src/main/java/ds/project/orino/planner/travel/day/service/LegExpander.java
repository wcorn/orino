package ds.project.orino.planner.travel.day.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 구간(도시 + 일수)을 <b>날짜별 기준 도시</b>로 편다. 입력은 구간이고 진실은 날짜다.
 *
 * <pre>
 * [오사카 3일][교토 1일][나고야 1일] + 10.24–11.02
 *   → 10.24·25·26 오사카 / 10.27 교토 / 10.28 나고야 / 10.29~11.02 나고야(상속)
 * </pre>
 *
 * <p><b>합계와 기간이 달라도 막지 않는다.</b> 여행을 짜는 중간 상태가 대부분 불일치라,
 * 400으로 거절하면 도시를 하나 추가할 때마다 기간을 먼저 늘려야 한다.
 *
 * <ul>
 *   <li>합계가 <b>모자라면</b> 남은 날짜가 마지막 구간 도시를 이어 쓴다</li>
 *   <li>합계가 <b>넘치면</b> 기간을 채운 시점에서 뒤 구간이 잘린다</li>
 * </ul>
 *
 * <p>순수 계산이라 서비스에서 떼어 둔다 — 이 규칙이 v2.1 입력의 전부라 따로 검증할 수 있어야
 * 한다.
 */
public final class LegExpander {

    private LegExpander() {
    }

    /**
     * @param legs 구간별 (기준 도시 장소 id, 일수). 순서가 곧 방문 순서다
     * @return 날짜 → 기준 도시 장소 id. 기간의 <b>모든</b> 날짜가 들어 있다
     */
    public static Map<LocalDate, Long> expand(LocalDate startDate, LocalDate endDate,
                                              List<Leg> legs) {
        if (legs.isEmpty()) {
            throw new IllegalArgumentException("구간이 하나도 없으면 날짜에 기준 도시를 줄 수 없습니다.");
        }
        Map<LocalDate, Long> byDate = new LinkedHashMap<>();
        int legIndex = 0;
        int usedInLeg = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            // 현재 구간의 일수를 다 쓰면 다음 구간으로. 마지막 구간에서는 더 갈 곳이 없어
            // 그대로 머무는데, 그게 "남은 날짜가 마지막 도시를 상속한다"는 규칙이다.
            while (usedInLeg >= legs.get(legIndex).days() && legIndex < legs.size() - 1) {
                legIndex++;
                usedInLeg = 0;
            }
            byDate.put(date, legs.get(legIndex).basePlaceId());
            usedInLeg++;
        }
        return byDate;
    }

    /**
     * 해석이 끝난 구간 하나.
     *
     * @param days 1 이상. 0을 허용하면 도시가 하루도 없는 구간이 생겨 순서만 차지한다
     */
    public record Leg(Long basePlaceId, int days) {

        public Leg {
            if (days < 1) {
                throw new IllegalArgumentException("구간의 일수는 1일 이상이어야 합니다.");
            }
        }
    }
}
