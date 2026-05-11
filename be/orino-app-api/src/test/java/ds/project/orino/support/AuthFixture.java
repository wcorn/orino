package ds.project.orino.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public final class AuthFixture {

    private AuthFixture() {
    }

    public static String loginAndGetAccessToken(MockMvc mockMvc, String loginId, String rawPassword)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "%s", "password": "%s"}
                                """.formatted(loginId, rawPassword)))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    public static String loginAndGetAccessToken(MockMvc mockMvc) throws Exception {
        return loginAndGetAccessToken(mockMvc, MemberFixture.DEFAULT_LOGIN_ID, MemberFixture.DEFAULT_PASSWORD);
    }
}
