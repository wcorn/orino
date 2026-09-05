package ds.project.orino.domain.planner.travel.entity;

/**
 * 준비 항목의 분류(v2.2 §11). <b>넷뿐이고, 다섯 번째를 만들지 않는다.</b>
 *
 * <p>분류를 늘리면 「어디에 넣지」가 준비 자체보다 오래 걸린다. 애매한 것은 전부
 * {@link #TODO}로 보낸다 — 그래서 서버 기본값도 {@code TODO}다.
 *
 * <p>화면은 항상 이 선언 순서로 그린다. 출발에 가까운 것부터 위에 온다.
 */
public enum PrepCategory {

    /** 여권·비자·보험처럼 없으면 못 떠나는 것. */
    DOCUMENT,

    /** 항공·숙소·입장권처럼 미리 잡아 두는 것. 기한이 붙는 항목이 대개 여기다. */
    BOOKING,

    /** 가방에 넣는 것. <b>수량을 갖는 유일한 분류다.</b> */
    BAG,

    /** 나머지 전부. 애매하면 여기다. */
    TODO
}
