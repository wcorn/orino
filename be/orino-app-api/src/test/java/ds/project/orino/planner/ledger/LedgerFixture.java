package ds.project.orino.planner.ledger;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 가계부 테스트가 공유하는 준비물. 자산과 카테고리는 거의 모든 테스트가 필요로 한다. */
public final class LedgerFixture {

    private LedgerFixture() {
    }

    public static long createAsset(MockMvc mockMvc, String authHeader,
                                   String name, String type) throws Exception {
        return createAsset(mockMvc, authHeader, name, type, null);
    }

    /** {@code linkedAssetId}는 체크카드에만 의미가 있다 — 그 밖에는 {@code null}로 둔다. */
    public static long createAsset(MockMvc mockMvc, String authHeader,
                                   String name, String type, Long linkedAssetId) throws Exception {
        String link = linkedAssetId == null ? "" : ", \"linkedAssetId\": " + linkedAssetId;
        String body = mockMvc.perform(post("/api/ledger/assets")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "type": "%s"%s}
                                """.formatted(name, type, link)))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 프리셋에서 이름으로 카테고리를 찾는다. 최초 진입 시 심기므로 목록을 한 번 부르면 있다. */
    public static long categoryIdByName(MockMvc mockMvc, String authHeader,
                                        String flow, String name) throws Exception {
        String body = mockMvc.perform(get("/api/ledger/categories")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("flow", flow))
                .andReturn().getResponse().getContentAsString();
        // 필터 경로는 값이 하나여도 리스트로 온다.
        List<Object> ids = JsonPath.read(body, "$.data[?(@.name == '%s')].id".formatted(name));
        return ((Number) ids.get(0)).longValue();
    }

    public static String createTransaction(MockMvc mockMvc, String authHeader, String json)
            throws Exception {
        return mockMvc.perform(post("/api/ledger/transactions")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn().getResponse().getContentAsString();
    }

    public static long transactionId(String responseBody) {
        return ((Number) JsonPath.read(responseBody, "$.data.transaction.id")).longValue();
    }
}
