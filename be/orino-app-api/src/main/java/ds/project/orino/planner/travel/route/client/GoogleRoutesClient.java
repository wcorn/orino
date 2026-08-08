package ds.project.orino.planner.travel.route.client;

import ds.project.orino.planner.travel.route.config.RoutesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/** Google Routes {@code computeRoutes}. 실패는 예외 대신 빈 값으로 돌려준다. */
@Component
public class GoogleRoutesClient implements RoutesClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleRoutesClient.class);

    /** 필드마스크가 곧 과금 등급이다 — 소요 시간과 거리만 받는다(경로 폴리라인은 쓰지 않는다). */
    private static final String FIELD_MASK = "routes.duration,routes.distanceMeters";

    private final RestClient restClient;
    private final RoutesProperties props;

    public GoogleRoutesClient(RoutesProperties props) {
        this.props = props;
        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(requestFactory(props))
                .build();
    }

    private static ClientHttpRequestFactory requestFactory(RoutesProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        return factory;
    }

    @Override
    public Optional<Route> route(Coordinates origin, Coordinates destination, TravelMode mode) {
        if (!props.enabled()) {
            return Optional.empty();
        }
        try {
            JsonNode root = restClient.post()
                    .uri("/directions/v2:computeRoutes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Goog-Api-Key", props.apiKey())
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .body(body(origin, destination, mode))
                    .retrieve()
                    .body(JsonNode.class);
            return parse(root);
        } catch (Exception e) {
            // 경로를 못 얻는 건 흔한 일이다(섬·해외 구간). 직선거리로 대체된다.
            log.warn("Routes 조회 실패: mode={}, {}", mode, e.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Object> body(Coordinates origin, Coordinates destination, TravelMode mode) {
        return Map.of(
                "origin", waypoint(origin),
                "destination", waypoint(destination),
                "travelMode", mode.name(),
                "languageCode", props.languageCode(),
                "units", "METRIC");
    }

    private static Map<String, Object> waypoint(Coordinates c) {
        return Map.of("location", Map.of("latLng",
                Map.of("latitude", c.lat(), "longitude", c.lng())));
    }

    private static Optional<Route> parse(JsonNode root) {
        JsonNode routes = root == null ? null : root.get("routes");
        if (routes == null || !routes.isArray() || routes.isEmpty()) {
            // 경로 없음도 정상 응답이다(빈 routes 배열).
            return Optional.empty();
        }
        JsonNode first = routes.get(0);
        Integer seconds = durationSeconds(first.get("duration"));
        JsonNode meters = first.get("distanceMeters");
        if (seconds == null || meters == null) {
            return Optional.empty();
        }
        return Optional.of(new Route(seconds, meters.asInt()));
    }

    /** {@code duration}은 {@code "1074s"} 형태의 문자열로 온다. */
    private static Integer durationSeconds(JsonNode duration) {
        if (duration == null || duration.isNull()) {
            return null;
        }
        String text = duration.asString();
        if (text == null || !text.endsWith("s")) {
            return null;
        }
        try {
            return new BigDecimal(text.substring(0, text.length() - 1)).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
