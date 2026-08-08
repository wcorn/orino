package ds.project.orino.planner.travel.tools.client;

import ds.project.orino.planner.travel.tools.dto.WeatherResponse;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 예보 조회.
 *
 * <p>인터페이스로 두는 이유는 테스트다. 실제 Open-Meteo는 <b>오늘부터 16일</b>만 주므로,
 * 여행 날짜를 고정해 둔 테스트는 시간이 지나면 전부 범위 밖이 되어 무너진다.
 */
public interface WeatherClient {

    /**
     * 그 좌표의 예보를 받는다. 실패하면 빈 값 — 날씨 때문에 화면이 죽으면 안 된다(§9).
     *
     * @param timezone 여행 타임존. 시간대별 예보를 <b>현지 벽시계</b>로 받기 위해 넘긴다
     */
    Optional<WeatherResponse> forecast(BigDecimal lat, BigDecimal lng, String timezone);
}
