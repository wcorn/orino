package ds.project.orino.planner.travel.tools;

import ds.project.orino.planner.travel.tools.client.WeatherClient;
import ds.project.orino.planner.travel.tools.dto.WeatherResponse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
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
 *
 * <p><b>{@code fetchedAt}은 테스트 시계로 찍는다.</b> 실제 클라이언트도 받아 온 순간의
 * {@code clock.instant()}를 넣고, 서비스는 그 값으로 신선도를 판정한다(#1357) — 여기서만
 * 고정 날짜를 쓰면 스텁이 준 예보가 항상 「만료됨」이라 갱신이 계속 걸린다.
 */
public class StubWeatherClient implements WeatherClient {

    public final List<String> calls = new ArrayList<>();

    private final Clock clock;

    public StubWeatherClient(Clock clock) {
        this.clock = clock;
        this.result = Optional.of(WeatherResponse.empty(clock.instant()));
    }

    /** 비우면 조회 실패 — 날씨가 없어도 화면이 사는지 확인할 때 쓴다. */
    public Optional<WeatherResponse> result;

    /**
     * 좌표마다 다른 예보를 주고 싶을 때. 도시별 조회가 정말 도시별인지 보려면 응답이 달라야
     * 한다 — 같은 값이면 어느 도시 것이 붙었는지 구별되지 않는다.
     */
    public java.util.function.Function<String, WeatherResponse> byCoordinates;

    /**
     * 조회가 느린 상황. 보드가 <b>날씨를 기다리지 않는지</b>를 보려면 느린 외부가 필요하다
     * (#1357) — 빠른 스텁으로는 마감시한이 있으나 없으나 결과가 같다.
     */
    public Duration delay = Duration.ZERO;

    @Override
    public Optional<WeatherResponse> forecast(BigDecimal lat, BigDecimal lng, String timezone) {
        String key = "%s,%s".formatted(lat, lng);
        calls.add("%s:%s".formatted(key, timezone));
        if (!delay.isZero()) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        if (byCoordinates != null) {
            return Optional.of(byCoordinates.apply(key));
        }
        return result;
    }

    /** 주어진 날짜들에 예보가 있는 응답을 만든다. */
    public void withDays(List<WeatherResponse.DailyWeather> daily,
                         Map<java.time.LocalDate, List<WeatherResponse.HourlyWeather>> hourly) {
        result = Optional.of(new WeatherResponse(WeatherResponse.SOURCE,
                WeatherResponse.LICENSE, fetchedAt(), daily, hourly));
    }

    /** 받아 온 순간. 테스트 시계를 그대로 쓴다 — 실제 클라이언트와 같은 값이다. */
    public Instant fetchedAt() {
        return clock.instant();
    }

    public void reset() {
        calls.clear();
        byCoordinates = null;
        delay = Duration.ZERO;
        result = Optional.of(WeatherResponse.empty(clock.instant()));
    }
}
