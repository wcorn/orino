package ds.project.orino.planner.travel.tools.client;

import ds.project.orino.planner.travel.tools.config.ToolsProperties;
import ds.project.orino.planner.travel.tools.dto.WeatherIcon;
import ds.project.orino.planner.travel.tools.dto.WeatherResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Open-Meteo 예보. 무료·무인증이다.
 *
 * <p><b>날짜 범위를 지정하지 않는다.</b> Open-Meteo는 예보 범위(오늘부터 16일) 밖을 요청하면
 * 빈 결과가 아니라 <b>에러</b>를 준다 — 여행이 아직 한참 남은 계획 단계에서는 그게 정상이므로,
 * 범위를 요청하는 대신 <b>받을 수 있는 만큼 받아 오고</b> 걸러내는 일은 서비스가 한다.
 *
 * <p>응답이 열 지향이다({@code time[]}, {@code weather_code[]}가 각각 배열). 인덱스로 맞춰 읽는다.
 */
@Component
public class OpenMeteoClient implements WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoClient.class);

    /** Open-Meteo가 허용하는 최대 예보 일수. */
    private static final int FORECAST_DAYS = 16;

    private final RestClient restClient;
    private final ToolsProperties props;
    private final Clock clock;

    public OpenMeteoClient(ToolsProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        this.restClient = RestClient.builder()
                .baseUrl(props.weatherBaseUrl())
                .requestFactory(requestFactory(props))
                .build();
    }

    private static ClientHttpRequestFactory requestFactory(ToolsProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        return factory;
    }

    @Override
    public Optional<WeatherResponse> forecast(BigDecimal lat, BigDecimal lng, String timezone) {
        try {
            JsonNode root = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/forecast")
                            .queryParam("latitude", lat)
                            .queryParam("longitude", lng)
                            .queryParam("daily", "weather_code,temperature_2m_max,"
                                    + "temperature_2m_min,precipitation_probability_max")
                            .queryParam("hourly", "weather_code,temperature_2m")
                            .queryParam("timezone", timezone)
                            .queryParam("forecast_days", FORECAST_DAYS)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            if (root == null || root.has("error")) {
                return Optional.empty();
            }
            return Optional.of(new WeatherResponse(
                    WeatherResponse.SOURCE, WeatherResponse.LICENSE, clock.instant(),
                    daily(root.get("daily")), hourly(root.get("hourly"))));
        } catch (Exception e) {
            // 날씨는 부가 정보다. 못 얻어도 보드는 그대로 떠야 한다.
            log.warn("Open-Meteo 조회 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static List<WeatherResponse.DailyWeather> daily(JsonNode node) {
        JsonNode times = node == null ? null : node.get("time");
        if (times == null || !times.isArray()) {
            return List.of();
        }
        List<WeatherResponse.DailyWeather> days = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            days.add(new WeatherResponse.DailyWeather(
                    LocalDate.parse(times.get(i).asString()),
                    icon(node.get("weather_code"), i),
                    rounded(node.get("temperature_2m_max"), i),
                    rounded(node.get("temperature_2m_min"), i),
                    rounded(node.get("precipitation_probability_max"), i)));
        }
        return days;
    }

    /** 시간대별은 날짜로 묶어 준다 — 화면이 하루를 골라 보기 때문이다. */
    private static Map<LocalDate, List<WeatherResponse.HourlyWeather>> hourly(JsonNode node) {
        JsonNode times = node == null ? null : node.get("time");
        if (times == null || !times.isArray()) {
            return Map.of();
        }
        Map<LocalDate, List<WeatherResponse.HourlyWeather>> byDate = new LinkedHashMap<>();
        for (int i = 0; i < times.size(); i++) {
            // "2026-08-08T09:00" — 이미 여행 타임존의 벽시계 값이다(timezone 파라미터).
            LocalDateTime at = LocalDateTime.parse(times.get(i).asString());
            byDate.computeIfAbsent(at.toLocalDate(), key -> new ArrayList<>())
                    .add(new WeatherResponse.HourlyWeather(
                            at.toLocalTime().toString(),
                            icon(node.get("weather_code"), i),
                            rounded(node.get("temperature_2m"), i)));
        }
        return byDate;
    }

    private static WeatherIcon icon(JsonNode codes, int index) {
        Integer code = rounded(codes, index);
        return code == null ? WeatherIcon.CLOUD : WeatherIcon.fromWmoCode(code);
    }

    /** 소수 온도는 화면에서 정수로만 쓴다. 여기서 한 번 반올림해 표기가 갈리지 않게 한다. */
    private static Integer rounded(JsonNode values, int index) {
        if (values == null || !values.isArray() || index >= values.size()) {
            return null;
        }
        JsonNode value = values.get(index);
        return value == null || value.isNull() ? null : Math.round((float) value.asDouble());
    }
}
