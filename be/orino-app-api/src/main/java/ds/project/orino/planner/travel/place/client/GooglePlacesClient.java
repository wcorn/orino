package ds.project.orino.planner.travel.place.client;

import ds.project.orino.planner.travel.place.config.PlacesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Places API (New) 호출 래퍼.
 *
 * <p>필드마스크를 반드시 보낸다 — Places API는 요청한 필드에 따라 <b>과금 등급이 갈린다</b>.
 * 전체를 받으면 쓰지도 않을 필드 때문에 비싼 등급으로 청구된다.
 *
 * <p>실패는 예외로 올리지 않고 빈 결과로 떨어뜨린다. 장소 검색이 안 된다고 일정 편집까지
 * 막을 이유가 없다(§외부 API 실패는 화면이 계속 동작해야 한다).
 */
@Component
public class GooglePlacesClient implements PlacesClient {

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesClient.class);

    /** 검색 목록에 필요한 최소 필드. */
    private static final String SEARCH_MASK = String.join(",",
            "places.id", "places.displayName", "places.formattedAddress",
            "places.location", "places.rating", "places.primaryTypeDisplayName");

    /** 도시 검색은 타임존·국가까지 필요하다(여행의 타임존·통화를 여기서 확정한다). */
    private static final String CITY_MASK = String.join(",",
            "places.id", "places.displayName", "places.formattedAddress",
            "places.location", "places.timeZone", "places.addressComponents");

    /** 상세는 영업시간·전화번호가 추가된다. */
    private static final String DETAILS_MASK = String.join(",",
            "id", "displayName", "formattedAddress", "location", "rating",
            "primaryTypeDisplayName", "nationalPhoneNumber", "regularOpeningHours",
            "timeZone", "addressComponents");

    private final RestClient restClient;
    private final PlacesProperties props;
    private final ObjectMapper objectMapper;

    public GooglePlacesClient(PlacesProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(requestFactory(props))
                .build();
    }

    private static ClientHttpRequestFactory requestFactory(PlacesProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        return factory;
    }

    @Override
    public List<PlaceResult> searchCities(String query) {
        // 행정구역만 받아 "도쿄 라멘집"이 목적지 후보로 뜨지 않게 한다.
        Map<String, Object> body = Map.of(
                "textQuery", query,
                "languageCode", props.languageCode(),
                "maxResultCount", 5,
                "includedType", "locality");
        return searchText(body, CITY_MASK);
    }

    @Override
    public List<PlaceResult> searchPlaces(String query, Coordinates bias) {
        var body = new java.util.HashMap<String, Object>();
        body.put("textQuery", query);
        body.put("languageCode", props.languageCode());
        body.put("maxResultCount", props.maxResults());
        if (bias != null) {
            // 편향이지 필터가 아니다 — 반경 밖 결과도 나올 수 있고, 그게 맞다.
            body.put("locationBias", Map.of("circle", Map.of(
                    "center", Map.of("latitude", bias.lat(), "longitude", bias.lng()),
                    "radius", (double) props.searchRadiusM())));
        }
        return searchText(body, SEARCH_MASK);
    }

    @Override
    public Optional<PlaceResult> fetchDetails(String googlePlaceId) {
        if (!props.enabled()) {
            return Optional.empty();
        }
        try {
            JsonNode node = restClient.get()
                    .uri("/v1/places/{id}?languageCode={lang}", googlePlaceId, props.languageCode())
                    .header("X-Goog-Api-Key", props.apiKey())
                    .header("X-Goog-FieldMask", DETAILS_MASK)
                    .retrieve()
                    .body(JsonNode.class);
            return Optional.ofNullable(node).map(this::toResult);
        } catch (Exception e) {
            log.warn("Places 상세 조회 실패: placeId={}, {}", googlePlaceId, e.getMessage());
            return Optional.empty();
        }
    }

    private List<PlaceResult> searchText(Map<String, Object> body, String fieldMask) {
        if (!props.enabled()) {
            return List.of();
        }
        try {
            JsonNode root = restClient.post()
                    .uri("/v1/places:searchText")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Goog-Api-Key", props.apiKey())
                    .header("X-Goog-FieldMask", fieldMask)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode places = root == null ? null : root.get("places");
            if (places == null || !places.isArray()) {
                // 결과 없음도 정상이다(검색어가 안 맞았을 뿐).
                return List.of();
            }
            List<PlaceResult> results = new ArrayList<>();
            for (JsonNode place : places) {
                results.add(toResult(place));
            }
            return results;
        } catch (Exception e) {
            log.warn("Places 검색 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private PlaceResult toResult(JsonNode node) {
        JsonNode location = node.get("location");
        return new PlaceResult(
                text(node, "id"),
                nestedText(node, "displayName", "text"),
                text(node, "formattedAddress"),
                decimal(location, "latitude"),
                decimal(location, "longitude"),
                nestedText(node, "primaryTypeDisplayName", "text"),
                decimal(node, "rating"),
                text(node, "nationalPhoneNumber"),
                rawJson(node.get("regularOpeningHours")),
                nestedText(node, "timeZone", "id"),
                countryCode(node.get("addressComponents")));
    }

    /** 주소 구성요소에서 국가 코드(alpha-2)를 뽑는다. 통화를 여기서 유도한다. */
    private String countryCode(JsonNode components) {
        if (components == null || !components.isArray()) {
            return null;
        }
        for (JsonNode component : components) {
            JsonNode types = component.get("types");
            if (types == null) {
                continue;
            }
            for (JsonNode type : types) {
                if ("country".equals(type.asString())) {
                    return text(component, "shortText");
                }
            }
        }
        return null;
    }

    private String rawJson(JsonNode node) {
        return node == null || node.isNull() ? null : objectMapper.writeValueAsString(node);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private String nestedText(JsonNode node, String field, String child) {
        JsonNode value = node == null ? null : node.get(field);
        return text(value, child);
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.decimalValue();
    }
}
