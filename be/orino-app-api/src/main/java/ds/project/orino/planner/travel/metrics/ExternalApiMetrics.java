package ds.project.orino.planner.travel.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 외부 API 호출 계측(#1158).
 *
 * <p><b>총 호출 수만 세면 캐시가 죽어도 그래프가 안 변한다</b> — 사용자 조작이 는 건지 캐시가
 * 깨진 건지 구분이 안 된다. 그래서 캐시 히트까지 함께 센다.
 *
 * <p>[ELOG-023](https://github.com/wcorn/orino/wiki/ELOG-023-travel-external-api-call-cost)은
 * 캐시가 호출을 줄이는지를 <b>통합 테스트의 스텁 카운터</b>로 고정했다. 테스트는 회귀를 막지만
 * 운영에서 실제로 몇 번 나갔는지는 말해 주지 않는다.
 *
 * <p><b>라벨은 enum으로만 받는다.</b> 검색어·좌표·장소 id가 라벨에 들어가면 시계열이 폭발하고,
 * Prometheus 저장이 늘어 Thanos S3 PUT까지 는다 — 관측이 과금을 만드는 그 함정이다. 문자열을
 * 받지 않으면 그 실수를 <b>애초에 쓸 수 없다.</b>
 */
@Component
public class ExternalApiMetrics {

    private static final String COUNTER = "orino.external.api.calls";

    /** 어떤 외부 API인가. 값이 고정돼 있어 시계열 수가 {@code Api × Result}로 묶인다. */
    public enum Api {
        /** 장소 검색(S-06). 유료. */
        PLACES_SEARCH("places_search"),
        /** 도시 검색(S-03 구간 입력). 유료. */
        PLACES_CITY("places_city"),
        /** 장소 상세 — 영업시간·전화. 유료. */
        PLACES_DETAILS("places_details"),
        // 이동시간(routes)은 없다(#1208). 사용자가 직접 넣으므로 나갈 호출이 없다.
        /** 날씨. 무료지만 도시 단위 캐시가 실제로 도는지 봐야 한다. */
        WEATHER("weather"),
        /** 환율. 무료. */
        FX("fx");

        private final String tag;

        Api(String tag) {
            this.tag = tag;
        }
    }

    /**
     * 호출의 결말.
     *
     * <p>{@code miss + error + rejected}가 <b>실제로 나간 호출 수</b>다 — 청구서에 오르는 값이
     * 그것이다. {@code hit / 전체}가 히트율이다.
     */
    public enum Result {
        /** 캐시에서 답했다. 외부 호출이 없다. */
        HIT("hit"),
        /** 외부로 나갔고 쓸 값을 받았다. */
        MISS("miss"),
        /**
         * 외부로 나갔는데 쓸 값이 없었다.
         *
         * <p>실패와 "결과 0건"을 구분하지 못한다 — 클라이언트가 둘 다 빈 값으로 돌려주기
         * 때문이다(§4.7의 실패 처리). 비용 관점에서는 어차피 같은 한 번의 호출이다.
         */
        ERROR("error"),
        /**
         * 구글이 거절했다(429·403).
         *
         * <p>{@code error}와 갈라 두는 이유는 <b>대처가 다르기 때문</b>이다 — 이쪽이 오르면
         * 하드캡(#1151)에 닿았거나 키·과금이 막힌 것이고, 저쪽이 오르면 구글이 흔들리거나
         * 우리 검색어가 아무것도 못 맞히는 것이다. 한 계열로 묶으면 그래프를 보고도 어느
         * 쪽인지 알 수 없다.
         */
        REJECTED("rejected");

        private final String tag;

        Result(String tag) {
            this.tag = tag;
        }
    }

    private final MeterRegistry registry;

    public ExternalApiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(Api api, Result result) {
        registry.counter(COUNTER, "api", api.tag, "result", result.tag).increment();
    }

    /** 나간 호출 한 건 — 쓸 값을 받았으면 {@code miss}, 비었으면 {@code error}. */
    public void recordFetch(Api api, boolean usable) {
        record(api, usable ? Result.MISS : Result.ERROR);
    }

    /** 거절당한 호출 한 건. 돈은 나갔을 수도 아닐 수도 있지만 사용자는 못 받았다. */
    public void recordRejected(Api api) {
        record(api, Result.REJECTED);
    }
}
