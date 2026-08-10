package ds.project.orino.domain.planner.travel.entity;

/**
 * 장소의 종류. <b>{@link #CITY}만 날짜의 기준 도시로 지정할 수 있다.</b>
 *
 * <p>구분을 두는 이유는 오지정을 막는 것이다 — "오사카성"을 기준 도시로 넣으면 타임존·통화는
 * 우연히 맞지만, 도시 일치 판정({@code city_place_ref})이 그 날짜의 모든 일정을
 * "다른 도시"로 만든다.
 */
public enum PlaceKind {

    /** 도시. 타임존·통화·검색 좌표·날씨의 기준점이 된다. */
    CITY,

    /** 일반 장소(가게·명소·숙소). */
    POI
}
