package ds.project.orino.planner.travel.route.service;

import java.math.BigDecimal;

/**
 * 두 좌표 사이 직선거리(m).
 *
 * <p>두 곳에 쓴다 — <b>이동수단을 정하는 기준</b>(1.5km 이하 도보)과, Routes가 실패했을 때
 * 보여줄 <b>대체값</b>이다. 어느 쪽도 정밀도가 중요하지 않아 지구를 구로 본다.
 */
public final class Haversine {

    /** 지구 평균 반지름(m). */
    private static final double EARTH_RADIUS_M = 6_371_000;

    private Haversine() {
    }

    public static int distanceM(BigDecimal lat1, BigDecimal lng1,
                                BigDecimal lat2, BigDecimal lng2) {
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double deltaPhi = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double deltaLambda = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2)
                * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        return (int) Math.round(EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }
}
