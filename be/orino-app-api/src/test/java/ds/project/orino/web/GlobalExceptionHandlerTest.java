package ds.project.orino.web;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest extends ApiTestSupport {

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

    /**
     * 없는 경로는 <b>404</b>다. 예전에는 500이었다(#1416) — 핸들러 없음
     * ({@code NoResourceFoundException})이 {@code @ExceptionHandler(Exception.class)}로
     * 떨어졌기 때문이다.
     *
     * <p>고친 이유는 지표다. 500은 알림·에러율에 잡히고 404는 안 잡히는데, 오타나 사라진
     * API를 부르는 옛 번들 때문에 장애 지표가 오르면 진짜 5xx가 그 사이에 묻힌다.
     *
     * <p><b>토큰이 있어야 여기까지 온다.</b> 시큐리티가 라우팅보다 먼저 걸려서, 인증 없이
     * 부르면 경로가 있든 없든 401이다(아래 {@link #securityRunsBeforeRouting}). 그래서
     * 이 버그를 실제로 겪는 것은 <b>토큰을 든 옛 클라이언트</b>뿐이고, 그게 정확히
     * 가계부를 지운 직후의 상황이다.
     */
    @Test
    @DisplayName("없는 경로는 404다 — 서버가 잘못한 것이 아니다")
    void handleUnknownPath() throws Exception {
        mockMvc.perform(get("/api/there-is-no-such-path")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GLB-ERR-004"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("사라진 API를 부르는 옛 클라이언트도 404를 받는다")
    void handleRemovedApi() throws Exception {
        // 가계부는 #1405에서 통째로 사라졌다. 안 새로고침한 탭이 한동안 이 경로를 부른다.
        mockMvc.perform(get("/api/ledger/transactions")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GLB-ERR-004"));
    }

    @Test
    @DisplayName("실재하는 경로는 그대로다 — 404로 뭉개지 않는다")
    void keepsExistingPaths() throws Exception {
        mockMvc.perform(get("/api/travel/summary")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }

    /**
     * 인증이 라우팅보다 먼저다. <b>없는 경로도 401</b>이라, 토큰 없이는 「그 주소가 있나」를
     * 알 수 없다 — 없애면 안 되는 성질이다.
     */
    @Test
    @DisplayName("토큰이 없으면 없는 경로도 401이다 — 주소의 존재 여부가 새지 않는다")
    void securityRunsBeforeRouting() throws Exception {
        mockMvc.perform(get("/api/there-is-no-such-path"))
                .andExpect(status().isUnauthorized());
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
