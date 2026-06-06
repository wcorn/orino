package ds.project.orino.planner.image.controller;

import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImageControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    @Test
    @DisplayName("POST upload-url - presigned 업로드 URL과 공개 URL을 발급한다")
    void createUploadUrl() throws Exception {
        mockMvc.perform(post("/api/planner/images/upload-url")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contentType": "image/png"}
                                """))
                .andExpect(status().isOk())
                // uploadUrl: presigned (서명 쿼리 포함), note-images 버킷, png 확장
                .andExpect(jsonPath("$.data.uploadUrl", containsString("note-images/")))
                .andExpect(jsonPath("$.data.uploadUrl", containsString("X-Amz-Signature")))
                // publicUrl: img.orino.dev 공개 주소
                .andExpect(jsonPath("$.data.publicUrl", startsWith("https://img.orino.dev/note-images/")))
                .andExpect(jsonPath("$.data.publicUrl", containsString(".png")));
    }

    @Test
    @DisplayName("POST upload-url - 이미지가 아닌 contentType은 400")
    void rejectNonImage() throws Exception {
        mockMvc.perform(post("/api/planner/images/upload-url")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contentType": "application/pdf"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST upload-url - 인증 없으면 401")
    void requiresAuth() throws Exception {
        mockMvc.perform(post("/api/planner/images/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contentType": "image/png"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
