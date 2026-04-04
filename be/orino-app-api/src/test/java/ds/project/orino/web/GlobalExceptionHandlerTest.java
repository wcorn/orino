package ds.project.orino.web;

import ds.project.orino.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest extends ApiTestSupport {

    @Test
    @DisplayName("CustomException 발생 시 해당 ErrorCode의 HTTP 상태와 코드를 반환한다")
    void handleCustomException() throws Exception {
        mockMvc.perform(post("/api/auth/reissue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-ERR-002"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드 요청 시 405를 반환한다")
    void handleMethodNotSupported() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("GLB-ERR-002"));
    }

    @Test
    @DisplayName("유효성 검증 실패 시 400과 검증 메시지를 반환한다")
    void handleValidationError() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "", "password": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLB-ERR-001"))
                .andExpect(jsonPath("$.message").exists());
    }
}
