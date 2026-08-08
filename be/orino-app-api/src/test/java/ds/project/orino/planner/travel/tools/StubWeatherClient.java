package ds.project.orino.planner.travel.tools;

import ds.project.orino.planner.travel.tools.client.WeatherClient;
import ds.project.orino.planner.travel.tools.dto.WeatherResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 테스트용 예보 스텁.
 *
 * <p>실제 Open-Meteo는 <b>오늘부터 16일</b>만 준다. 날짜를 고정한 테스트가 실제 API를 부르면
 * 시간이 지나는 것만으로 무너진다 — 예보 범위 자체를 테스트가 정해야 한다.
 */
public class StubWeatherClient implements WeatherClient {

    public final List<String> calls = new ArrayList<>();

    /** 비우면 조회 실패 — 날씨가 없어도 화면이 사는지 확인할 때 쓴다. */
    public Optional<WeatherResponse> result = Optional.of(
            WeatherResponse.empty(Instant.parse("2026-08-08T00:00:00Z")));

    @Override
    public Optional<WeatherResponse> forecast(BigDecimal lat, BigDecimal lng, String timezone) {
        calls.add("%s,%s:%s".formatted(lat, lng, timezone));
        return result;
    }

    /** 주어진 날짜들에 예보가 있는 응답을 만든다. */
    public void withDays(List<WeatherResponse.DailyWeather> daily,
                         Map<java.time.LocalDate, List<WeatherResponse.HourlyWeather>> hourly) {
        result = Optional.of(new WeatherResponse(WeatherResponse.SOURCE,
                WeatherResponse.LICENSE, Instant.parse("2026-08-08T00:00:00Z"), daily, hourly));
    }

    public void reset() {
        calls.clear();
        result = Optional.of(WeatherResponse.empty(Instant.parse("2026-08-08T00:00:00Z")));
    }
}
