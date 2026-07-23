package ds.project.orino.planner.lifelog.image.controller;

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

class LifelogImageControllerTest extends ApiTestSupport {

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
    @DisplayName("POST upload-url ORIGINAL - lifelog/moments 경로로 presigned URL·objectKey를 발급한다")
    void createOriginalUploadUrl() throws Exception {
        mockMvc.perform(post("/api/lifelog/images/upload-url")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contentType": "image/jpeg", "kind": "ORIGINAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl", containsString("note-images/lifelog/moments/")))
                .andExpect(jsonPath("$.data.uploadUrl", containsString("X-Amz-Signature")))
                .andExpect(jsonPath("$.data.publicUrl",
                        startsWith("https://img.orino.dev/note-images/lifelog/moments/")))
                .andExpect(jsonPath("$.data.publicUrl", containsString(".jpg")))
                .andExpect(jsonPath("$.data.objectKey", startsWith("lifelog/moments/")))
                .andExpect(jsonPath("$.data.objectKey", containsString(".jpg")));
    }

    @Test
    @DisplayName("POST upload-url THUMB - lifelog/thumbs 경로로 발급한다")
    void createThumbUploadUrl() throws Exception {
        mockMvc.perform(post("/api/lifelog/images/upload-url")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contentType": "image/webp", "kind": "THUMB"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.objectKey", startsWith("lifelog/thumbs/")))
                .andExpect(jsonPath("$.data.objectKey", containsString(".webp")));
    }

    @Test
    @DisplayName("POST upload-url - 이미지가 아닌 contentType은 400")
    void rejectNonImage() throws Exception {
        mockMvc.perform(post("/api/lifelog/images/upload-url")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contentType": "application/pdf", "kind": "ORIGINAL"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST upload-url - kind 누락은 400")
    void rejectMissingKind() throws Exception {
        mockMvc.perform(post("/api/lifelog/images/upload-url")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contentType": "image/jpeg"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST upload-url - 인증 없으면 401")
    void requiresAuth() throws Exception {
        mockMvc.perform(post("/api/lifelog/images/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contentType": "image/jpeg", "kind": "ORIGINAL"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
