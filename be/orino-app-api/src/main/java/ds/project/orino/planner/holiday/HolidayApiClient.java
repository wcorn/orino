package ds.project.orino.planner.holiday;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 한국천문연구원 특일정보 {@code getRestDeInfo}(공휴일) 호출 래퍼.
 *
 * <p>data.go.kr 응답 특성에 방어적으로 파싱한다: {@code items.item}이 0건이면 빈 문자열,
 * 1건이면 객체, 2건 이상이면 배열로 내려온다. {@code locdate}(yyyyMMdd int)와 {@code dateName}을 읽는다.
 */
@Component
public class HolidayApiClient {

    private static final DateTimeFormatter LOCDATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient restClient;
    private final HolidayProperties properties;
    private final ObjectMapper objectMapper;

    public HolidayApiClient(HolidayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** 한 해의 공휴일(대체·임시 포함)을 조회한다. */
    public List<HolidayItem> fetchYear(int year) {
        URI uri = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path("/getRestDeInfo")
                .queryParam("serviceKey", properties.serviceKey())
                .queryParam("solYear", year)
                .queryParam("numOfRows", 100)
                .queryParam("_type", "json")
                .build()
                .toUri();

        String body = restClient.get().uri(uri).retrieve().body(String.class);
        return parse(body);
    }

    private List<HolidayItem> parse(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JacksonException e) {
            throw new IllegalStateException("특일정보 응답 파싱 실패: " + truncate(body), e);
        }

        String resultCode = root.path("response").path("header").path("resultCode").asString("");
        if (!resultCode.isEmpty() && !"00".equals(resultCode)) {
            String msg = root.path("response").path("header").path("resultMsg").asString("");
            throw new IllegalStateException("특일정보 오류 " + resultCode + ": " + msg);
        }

        JsonNode item = root.path("response").path("body").path("items").path("item");
        List<HolidayItem> result = new ArrayList<>();
        if (item.isArray()) {
            for (JsonNode node : item) {
                addIfHoliday(result, node);
            }
        } else if (item.isObject()) {
            addIfHoliday(result, item);
        }
        return result;
    }

    private void addIfHoliday(List<HolidayItem> result, JsonNode node) {
        if (!"Y".equals(node.path("isHoliday").asString("Y"))) {
            return;
        }
        String locdate = node.path("locdate").asString("");
        String name = node.path("dateName").asString("");
        if (locdate.isBlank() || name.isBlank()) {
            return;
        }
        result.add(new HolidayItem(LocalDate.parse(locdate, LOCDATE), name));
    }

    private static String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    /** 특일정보 1건(공휴일 날짜·이름). */
    public record HolidayItem(LocalDate date, String name) {
    }
}
