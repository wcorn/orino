package ds.project.orino.planner.travel.route.client;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 두 지점 사이 경로 조회.
 *
 * <p>구현을 갈아끼울 수 있게 인터페이스로 둔다 — 테스트에서 실제 구글을 부르면 유료 호출이
 * 발생하고, 무엇보다 호출 횟수를 세어 <b>캐시가 실제로 호출을 줄이는지</b> 확인할 수 없다.
 */
public interface RoutesClient {

    /**
     * 경로를 조회한다. 예외를 던지지 않는다(거절 제외) — 이동시간 때문에 보드가 죽으면 안 된다.
     *
     * <p>거절(429·403)만 {@code ExternalApiRejectedException}으로 올라간다. 대처가 다르기
     * 때문이다 — 그쪽은 하드캡이나 키 문제고, 여기 담기는 실패는 구글이나 좌표 문제다.
     */
    RouteLookup route(Coordinates origin, Coordinates destination, TravelMode mode);

    /**
     * 조회의 결말.
     *
     * <p><b>{@code NO_ROUTE}와 {@code FAILED}를 가르는 것이 이 타입의 존재 이유다.</b> 예전에는
     * 둘 다 빈 {@code Optional}이었고, 그래서 호출부는 "이 빈 값을 캐시해도 되는가"를 판단할 수
     * 없었다. 캐시하지 않는 쪽으로 통일했더니 <b>경로가 없는 구간은 보드를 열 때마다 같은 유료
     * 호출을 다시 냈다</b>(#1203).
     */
    enum Outcome {
        /** 쓸 수 있는 경로를 받았다. */
        FOUND,
        /**
         * 구글이 정상 응답했고 경로가 없다.
         *
         * <p><b>영구적이다.</b> 섬·해외 구간처럼 애초에 그 수단으로 갈 수 없는 두 지점이다.
         * 좌표가 바뀌면 캐시 키가 바뀌므로 이 값이 잘못 재사용될 걱정은 없다.
         */
        NO_ROUTE,
        /**
         * 부르지 못했거나 응답을 쓸 수 없었다(타임아웃·5xx·형식 오류).
         *
         * <p><b>일시적이다.</b> 붙들고 있으면 구글이 복구된 뒤에도 계속 fallback이라 캐시하지
         * 않는다.
         */
        FAILED,
        /**
         * API 키가 없어 아예 부르지 않았다.
         *
         * <p>{@code FAILED}와 가르는 이유는 <b>나간 호출이 없기 때문</b>이다. 이걸 실패로 세면
         * 키를 안 넣은 환경에서 "유료 호출이 계속 실패하는 중"으로 보인다.
         */
        DISABLED
    }

    /** 결말과 (있다면) 경로. {@code route}는 {@code FOUND}일 때만 채워진다. */
    record RouteLookup(Outcome outcome, Route route) {

        public static RouteLookup found(Route route) {
            return new RouteLookup(Outcome.FOUND, Objects.requireNonNull(route));
        }

        public static RouteLookup noRoute() {
            return new RouteLookup(Outcome.NO_ROUTE, null);
        }

        public static RouteLookup failed() {
            return new RouteLookup(Outcome.FAILED, null);
        }

        public static RouteLookup disabled() {
            return new RouteLookup(Outcome.DISABLED, null);
        }

        /** 호출이 실제로 나갔는가. 비용 계측은 이 값이 참일 때만 센다. */
        public boolean calledOut() {
            return outcome != Outcome.DISABLED;
        }
    }

    record Coordinates(BigDecimal lat, BigDecimal lng) {
    }

    /**
     * @param durationSeconds 구글이 준 소요 시간
     * @param distanceM       실제 경로 거리(직선거리가 아니다)
     */
    record Route(int durationSeconds, int distanceM) {
    }
}
