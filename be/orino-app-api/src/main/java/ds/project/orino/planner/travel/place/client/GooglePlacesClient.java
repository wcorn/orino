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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
            "places.location", "places.timeZone", "places.addressComponents", "places.types");

    /**
     * 목적지로 인정하는 Google place type.
     *
     * <p>도시가 어느 단계로 잡히는지는 나라마다 다르다 — 오사카·런던·뉴욕은 {@code locality}지만
     * 도쿄도는 {@code administrative_area_level_1}, 파리는 {@code administrative_area_level_3}다.
     * 그래서 {@code locality} 하나로 못 걸러낸다.
     */
    private static final Set<String> DESTINATION_TYPES = Set.of(
            "locality", "postal_town", "administrative_area_level_1",
            "administrative_area_level_2", "administrative_area_level_3", "country");

    /** 걸러내기 전에 받아 올 후보 수. 진짜 도시가 상위에 없을 수 있어 넉넉히 받는다. */
    private static final int CITY_CANDIDATE_COUNT = 20;

    /** 걸러낸 뒤 보여줄 수. 목적지는 하나만 고르는 것이라 길 필요가 없다. */
    private static final int CITY_RESULT_COUNT = 5;

    /** 같은 검색어에 여러 단계가 걸리면 좁은 쪽(도시)을 먼저 보여준다. */
    private static final List<String> TYPE_PRIORITY = List.of(
            "locality", "postal_town", "administrative_area_level_3",
            "administrative_area_level_2", "administrative_area_level_1", "country");

    /**
     * 상세는 영업시간·전화번호·사진이 추가된다.
     *
     * <p><b>검색 마스크에는 photos를 넣지 않는다.</b> 검색 결과는 20개인데 거기까지 사진을
     * 달면 화면 한 번에 20장을 받아야 하고, 그건 그대로 유료 호출 20번이다. 사진은 담아 둔
     * 장소에만 붙인다.
     */
    private static final String DETAILS_MASK = String.join(",",
            "id", "displayName", "formattedAddress", "location", "rating",
            "primaryTypeDisplayName", "nationalPhoneNumber", "regularOpeningHours",
            "timeZone", "addressComponents", "photos");

    /**
     * 받아 올 사진의 최대 가로 픽셀.
     *
     * <p>장소 블록의 미리보기용이라 원본이 필요 없다. 크게 받으면 저장 용량과 전송량만
     * 늘고, 이 사진은 오프라인 캐시에도 실린다.
     */
    private static final int PHOTO_MAX_WIDTH = 800;

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
        // includedType은 <b>힌트일 뿐</b>이라(strictTypeFiltering을 켜면 도쿄도·파리가 통째로
        // 사라진다) 실제 걸러내기는 응답의 types로 한다. 그래서 넉넉히 받아 온다 —
        // "파리"는 상위 5개가 전부 파리바게뜨 지점이고 진짜 파리는 그 뒤에 있다.
        Map<String, Object> body = Map.of(
                "textQuery", query,
                "languageCode", props.languageCode(),
                "maxResultCount", CITY_CANDIDATE_COUNT,
                "includedType", "locality");
        return selectDestinations(searchText(body, CITY_MASK));
    }

    /**
     * 받아 온 후보에서 목적지가 될 만한 것만 골라 좁은 순으로 정렬한다.
     *
     * <p>구글 호출과 떼어 둔다 — 걸러내기 규칙이 이 기능의 전부라 따로 검증할 수 있어야 한다.
     */
    static List<PlaceResult> selectDestinations(List<PlaceResult> candidates) {
        return candidates.stream()
                .filter(city -> city.types().stream().anyMatch(DESTINATION_TYPES::contains))
                .sorted(Comparator.comparingInt(GooglePlacesClient::typeRank))
                .limit(CITY_RESULT_COUNT)
                .toList();
    }

    /** 좁은 행정구역일수록 앞. 목록에 없는 타입은 맨 뒤로 보낸다. */
    private static int typeRank(PlaceResult city) {
        int best = TYPE_PRIORITY.size();
        for (String type : city.types()) {
            int rank = TYPE_PRIORITY.indexOf(type);
            if (rank >= 0 && rank < best) {
                best = rank;
            }
        }
        return best;
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

    /**
     * 사진 바이트 받기 — <b>두 번 호출한다.</b>
     *
     * <p>구글의 media 엔드포인트는 기본적으로 CDN으로 <b>리다이렉트</b>하는데, 그 리다이렉트를
     * 따라가면 우리 API 키가 CDN 요청에 실려 나간다. {@code skipHttpRedirect=true}로 URI만
     * 받아 두 번째 호출에서 키 없이 내려받는다.
     *
     * <p>그 URI는 <b>만료된다</b>. 그래서 DB에 넣지 않고 바이트를 받아 우리 저장소에 넣는다.
     */
    @Override
    public Optional<byte[]> fetchPhoto(String photoName) {
        if (!props.enabled() || photoName == null || photoName.isBlank()) {
            return Optional.empty();
        }
        try {
            // 리소스 이름에 슬래시가 들어 있다(`places/X/photos/Y`). URI 템플릿 변수로 넘기면
            // 슬래시가 %2F로 인코딩돼 404가 난다 — 경로로 붙여야 한다.
            JsonNode node = restClient.get()
                    .uri(builder -> builder.path("/v1/" + photoName + "/media")
                            .queryParam("maxWidthPx", PHOTO_MAX_WIDTH)
                            .queryParam("skipHttpRedirect", true)
                            .build())
                    .header("X-Goog-Api-Key", props.apiKey())
                    .retrieve()
                    .body(JsonNode.class);

            String uri = text(node, "photoUri");
            if (uri == null) {
                return Optional.empty();
            }
            byte[] bytes = RestClient.create().get().uri(uri).retrieve().body(byte[].class);
            return Optional.ofNullable(bytes).filter(b -> b.length > 0);
        } catch (Exception e) {
            // 사진이 없다고 장소를 못 쓰게 만들지 않는다.
            log.warn("Places 사진 조회 실패: photo={}, {}", photoName, e.getMessage());
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
                countryCode(node.get("addressComponents")),
                types(node.get("types")),
                photoName(node.get("photos")),
                photoAttribution(node.get("photos")));
    }

    /** 대표 사진 하나만 쓴다 — 장소 블록에 한 장이면 충분하고, 장수만큼 유료 호출이 는다. */
    private String photoName(JsonNode photos) {
        JsonNode first = firstPhoto(photos);
        return first == null ? null : text(first, "name");
    }

    /**
     * 사진 저작자 표기. <b>구글 약관상 사진을 보여줄 때 함께 표시해야 한다</b> —
     * 사진만 캐시하고 표기를 버리면 쓸 수 없는 사진이 된다.
     */
    private String photoAttribution(JsonNode photos) {
        JsonNode first = firstPhoto(photos);
        if (first == null) {
            return null;
        }
        JsonNode authors = first.get("authorAttributions");
        if (authors == null || !authors.isArray() || authors.isEmpty()) {
            return null;
        }
        return text(authors.get(0), "displayName");
    }

    private static JsonNode firstPhoto(JsonNode photos) {
        return photos == null || !photos.isArray() || photos.isEmpty() ? null : photos.get(0);
    }

    private static List<String> types(JsonNode types) {
        if (types == null || !types.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode type : types) {
            values.add(type.asString());
        }
        return List.copyOf(values);
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
