package ds.project.orino.planner.lifelog.geocode.client;

import ds.project.orino.planner.lifelog.geocode.GeocodePlace;
import ds.project.orino.planner.lifelog.geocode.config.NominatimProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Nominatim(OSM) 공개 인스턴스 호출 래퍼.
 *
 * <p>정책 준수: 식별 User-Agent 헤더를 붙이고, 연속 호출을 {@link NominatimProperties#minInterval()}
 * 이상으로 벌린다(초당 1회 이하). 캐시는 상위 서비스가 흡수하므로 여기선 순수 호출만 한다.
 */
@Component
public class NominatimGeocodingClient implements GeocodingClient {

    private final RestClient restClient;
    private final NominatimProperties props;
    private final ObjectMapper objectMapper;

    private final ReentrantLock rateLock = new ReentrantLock(true);
    private long lastRequestNanos;

    public NominatimGeocodingClient(NominatimProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory(props))
                .defaultHeader(HttpHeaders.USER_AGENT, props.userAgent())
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, props.acceptLanguage())
                .build();
    }

    private static org.springframework.http.client.ClientHttpRequestFactory requestFactory(
            NominatimProperties props) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        return factory;
    }

    @Override
    public Optional<GeocodePlace> reverse(double lat, double lng) {
        URI uri = UriComponentsBuilder.fromUriString(props.baseUrl())
                .path("/reverse")
                .queryParam("format", "jsonv2")
                .queryParam("lat", lat)
                .queryParam("lon", lng)
                .build()
                .toUri();

        JsonNode root = getJson(uri);
        if (root == null || root.has("error") || !root.hasNonNull("display_name")) {
            return Optional.empty();
        }
        return Optional.of(toPlace(root));
    }

    @Override
    public List<GeocodePlace> search(String query, int limit) {
        URI uri = UriComponentsBuilder.fromUriString(props.baseUrl())
                .path("/search")
                .queryParam("format", "jsonv2")
                .queryParam("q", query)
                .queryParam("limit", limit)
                .build()
                .toUri();

        JsonNode root = getJson(uri);
        List<GeocodePlace> results = new ArrayList<>();
        if (root != null && root.isArray()) {
            for (JsonNode node : root) {
                if (node.hasNonNull("display_name")) {
                    results.add(toPlace(node));
                }
            }
        }
        return results;
    }

    private GeocodePlace toPlace(JsonNode node) {
        return new GeocodePlace(
                node.path("display_name").asString(""),
                new BigDecimal(node.path("lat").asString("0")),
                new BigDecimal(node.path("lon").asString("0")));
    }

    /** rate limit을 지키며 GET 하고 JSON으로 파싱한다. 파싱 실패는 예외. */
    private JsonNode getJson(URI uri) {
        throttle();
        String body = restClient.get().uri(uri).retrieve().body(String.class);
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException e) {
            throw new IllegalStateException("Nominatim 응답 파싱 실패", e);
        }
    }

    /** 직전 호출로부터 최소 간격이 지날 때까지 대기해 Nominatim rate limit을 지킨다. */
    private void throttle() {
        rateLock.lock();
        try {
            long minNanos = props.minInterval().toNanos();
            long waitNanos = minNanos - (System.nanoTime() - lastRequestNanos);
            if (waitNanos > 0) {
                try {
                    Thread.sleep(waitNanos / 1_000_000L, (int) (waitNanos % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestNanos = System.nanoTime();
        } finally {
            rateLock.unlock();
        }
    }
}
