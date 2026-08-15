package ds.project.orino.planner.travel.route.client;

import ds.project.orino.planner.travel.external.ExternalApiRejectedException;
import ds.project.orino.planner.travel.route.config.RoutesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Google Routes {@code computeRoutes}. 실패는 예외 대신 {@link RouteLookup}으로 돌려준다.
 *
 * <p>"경로가 없다"({@code NO_ROUTE})와 "못 불렀다"({@code FAILED})를 <b>여기서</b> 가른다.
 * 호출부는 응답을 다시 볼 수 없으므로, 이 구분을 여기서 지우면 되살릴 수 없다(#1203).
 */
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
    public RouteLookup route(Coordinates origin, Coordinates destination, TravelMode mode) {
        if (!props.enabled()) {
            return RouteLookup.disabled();
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
        } catch (HttpClientErrorException e) {
            // 429·403은 갈라낸다. 이동시간은 어차피 직선거리로 대체되지만, 대시보드에서
            // "경로를 못 찾는 구간이 많다"와 "캡에 걸렸다"가 같아 보이면 안 된다.
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 429 || status.value() == 403) {
                log.warn("Routes 거절: status={}, {}", status.value(), e.getMessage());
                throw new ExternalApiRejectedException("Routes 거절 (" + status.value() + ")");
            }
            log.warn("Routes 조회 실패: mode={}, {}", mode, e.getMessage());
            return RouteLookup.failed();
        } catch (Exception e) {
            // 타임아웃·연결 실패·본문 파싱 예외. 일시적이라 캐시하지 않는다.
            log.warn("Routes 조회 실패: mode={}, {}", mode, e.getMessage());
            return RouteLookup.failed();
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

    private static RouteLookup parse(JsonNode root) {
        if (root == null) {
            // 본문이 비어 왔다. 정상 응답이 아니라 프로토콜이 이상한 것이라 일시적으로 본다.
            return RouteLookup.failed();
        }
        JsonNode routes = root.get("routes");
        if (routes == null || !routes.isArray() || routes.isEmpty()) {
            // 경로 없음도 정상 응답이다(빈 routes 배열). 이건 영구적이라 캐시해도 된다.
            return RouteLookup.noRoute();
        }
        JsonNode first = routes.get(0);
        Integer seconds = durationSeconds(first.get("duration"));
        JsonNode meters = first.get("distanceMeters");
        if (seconds == null || meters == null) {
            // 경로는 왔는데 필요한 필드를 못 읽었다 — FIELD_MASK 나 응답 형식이 바뀐 쪽에
            // 가깝다. "경로가 없다"로 캐시해 버리면 우리 버그를 30일 굳혀 놓게 된다.
            return RouteLookup.failed();
        }
        return RouteLookup.found(new Route(seconds, meters.asInt()));
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
