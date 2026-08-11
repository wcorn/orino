package ds.project.orino.planner.travel.route;

import ds.project.orino.planner.travel.external.ExternalApiRejectedException;
import ds.project.orino.planner.travel.route.client.RoutesClient;
import ds.project.orino.planner.travel.route.client.TravelMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 테스트용 Routes 스텁.
 *
 * <p>호출 인자를 기록해 <b>수단 판정이 실제로 어떻게 내려졌는지</b>와
 * <b>캐시가 호출을 줄이는지</b>를 확인할 수 있게 한다.
 */
public class StubRoutesClient implements RoutesClient {

    public record Call(Coordinates origin, Coordinates destination, TravelMode mode) {
    }

    public final List<Call> calls = new ArrayList<>();

    /** 비우면 경로 없음 — fallback 경로를 탄다. */
    public Optional<Route> result = Optional.of(new Route(720, 900));

    /** 켜면 구글이 429·403으로 거절한 것처럼 군다(#1159). */
    public boolean reject = false;

    @Override
    public Optional<Route> route(Coordinates origin, Coordinates destination, TravelMode mode) {
        calls.add(new Call(origin, destination, mode));
        if (reject) {
            throw new ExternalApiRejectedException("stub 거절");
        }
        return result;
    }

    public void reset() {
        calls.clear();
        result = Optional.of(new Route(720, 900));
        reject = false;
    }
}
