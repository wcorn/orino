package ds.project.orino.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 여행을 만들려면 <b>기준 도시</b>가 먼저 있어야 한다(v2.1). 도시 검색은 구글을 타므로,
 * 테스트는 직접 입력 경로로 도시를 만들어 그 id를 구간에 넣는다.
 *
 * <p>구간 입력을 쓰는 테스트가 여럿이라 여기 모아 둔다 — 도시 만드는 JSON이 파일마다
 * 흩어지면 요청 형태가 또 바뀔 때 전부 다시 고쳐야 한다.
 */
public final class TravelCityFixture {

    private TravelCityFixture() {
    }

    /** 좌표 없는 도시. 날씨를 안 보는 테스트는 이걸로 충분하다. */
    public static long createCity(MockMvc mockMvc, String authHeader, String name,
                                  String timezone, String currency) throws Exception {
        return createCity(mockMvc, authHeader, name, timezone, currency, null, null);
    }

    public static long createCity(MockMvc mockMvc, String authHeader, String name,
                                  String timezone, String currency,
                                  String lat, String lng) throws Exception {
        String coords = lat == null ? "" : ", \"lat\": %s, \"lng\": %s".formatted(lat, lng);
        String body = mockMvc.perform(post("/api/travel/places")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "kind": "CITY",
                                 "timezone": "%s", "currency": "%s"%s}
                                """.formatted(name, timezone, currency, coords)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 여행 생성·수정 요청에 넣을 구간 한 개짜리 조각. */
    public static String singleLeg(long cityPlaceId, int days) {
        return "\"legs\": [{\"cityPlaceId\": %d, \"days\": %d}]".formatted(cityPlaceId, days);
    }
}
