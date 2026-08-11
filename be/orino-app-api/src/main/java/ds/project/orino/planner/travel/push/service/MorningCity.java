package ds.project.orino.planner.travel.push.service;

import ds.project.orino.domain.planner.travel.entity.TravelPlace;

import java.time.LocalDate;
import java.util.Map;

/**
 * 아침 요약이 기준으로 삼는 도시(v2.1 §3.6).
 *
 * <p>예약(발송 시각)과 발송(본문 문구)이 <b>같은 판정</b>을 써야 한다 — 두 벌이면 오사카 시각에
 * 보내면서 본문은 교토라고 말하는 날이 생긴다. 그래서 규칙을 여기 한 곳에 둔다.
 */
public final class MorningCity {

    private MorningCity() {
    }

    /**
     * 그날 아침에 <b>눈을 뜨는 도시</b>. 도시가 바뀌는 날은 아직 <b>전날 도시</b>에 있다.
     *
     * <p>첫날은 "바뀐 것"이 아니다 — 비교할 앞 날짜가 없다.
     */
    public static TravelPlace wakeUpIn(LocalDate date, Map<LocalDate, TravelPlace> cities) {
        TravelPlace yesterday = cities.get(date.minusDays(1));
        return changesOn(date, cities) ? yesterday : cities.get(date);
    }

    /**
     * 그날 도시가 바뀌는가. 판정은 <b>장소 id</b>로 한다 — 같은 도시를 다시 지정해도 행이
     * 같으면 바뀐 것이 아니고, 이름 비교는 표기 흔들림에 깨진다.
     */
    public static boolean changesOn(LocalDate date, Map<LocalDate, TravelPlace> cities) {
        TravelPlace today = cities.get(date);
        TravelPlace yesterday = cities.get(date.minusDays(1));
        if (today == null || yesterday == null) {
            return false;
        }
        return !today.getId().equals(yesterday.getId());
    }
}
