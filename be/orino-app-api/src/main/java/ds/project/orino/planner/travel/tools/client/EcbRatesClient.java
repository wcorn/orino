package ds.project.orino.planner.travel.tools.client;

import java.util.Optional;

/** ECB 고시 환율 조회. 실패는 예외 대신 빈 값이다 — 환율 때문에 화면이 죽지 않는다. */
public interface EcbRatesClient {

    Optional<EcbRates> latest();
}
