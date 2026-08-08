package ds.project.orino.planner.travel.route.client;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 두 지점 사이 경로 조회.
 *
 * <p>구현을 갈아끼울 수 있게 인터페이스로 둔다 — 테스트에서 실제 구글을 부르면 유료 호출이
 * 발생하고, 무엇보다 호출 횟수를 세어 <b>캐시가 실제로 호출을 줄이는지</b> 확인할 수 없다.
 */
public interface RoutesClient {

    /** 경로를 못 얻으면 빈 값. 예외를 던지지 않는다 — 이동시간 때문에 보드가 죽으면 안 된다. */
    Optional<Route> route(Coordinates origin, Coordinates destination, TravelMode mode);

    record Coordinates(BigDecimal lat, BigDecimal lng) {
    }

    /**
     * @param durationSeconds 구글이 준 소요 시간
     * @param distanceM       실제 경로 거리(직선거리가 아니다)
     */
    record Route(int durationSeconds, int distanceM) {
    }
}
