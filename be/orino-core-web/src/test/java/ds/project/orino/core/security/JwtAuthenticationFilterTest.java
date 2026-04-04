package ds.project.orino.core.security;

import ds.project.orino.core.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private FilterChain filterChain;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    @DisplayName("유효한 Bearer 토큰이면 SecurityContext에 인증 정보를 설정한다")
    void validToken_setsAuthentication() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer valid-token");
        given(jwtTokenProvider.validate("valid-token")).willReturn(true);
        given(jwtTokenProvider.getMemberId("valid-token")).willReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(1L);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("유효한 토큰이면 MDC에 userId를 설정한다")
    void validToken_setsMdcUserId() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer valid-token");
        given(jwtTokenProvider.validate("valid-token")).willReturn(true);
        given(jwtTokenProvider.getMemberId("valid-token")).willReturn(42L);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(MDC.get("userId")).isEqualTo("42");
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증 정보를 설정하지 않는다")
    void noHeader_skipsAuthentication() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Bearer 접두사가 없으면 인증 정보를 설정하지 않는다")
    void noBearerPrefix_skipsAuthentication() throws ServletException, IOException {
        request.addHeader("Authorization", "Basic some-token");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("유효하지 않은 토큰이면 인증 정보를 설정하지 않는다")
    void invalidToken_skipsAuthentication() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer invalid-token");
        given(jwtTokenProvider.validate("invalid-token")).willReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
