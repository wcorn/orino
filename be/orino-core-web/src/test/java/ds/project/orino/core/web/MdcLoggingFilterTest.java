package ds.project.orino.core.web;

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

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MdcLoggingFilterTest {

    private MdcLoggingFilter filter;

    @Mock
    private FilterChain filterChain;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new MdcLoggingFilter();
        request = new MockHttpServletRequest("GET", "/api/test");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("필터 체인 실행 중 MDC에 requestId, method, uri를 설정한다")
    void setsMdcContextDuringFilterChain() throws ServletException, IOException {
        willAnswer(invocation -> {
            assertThat(MDC.get("requestId")).isNotNull().hasSize(8);
            assertThat(MDC.get("method")).isEqualTo("GET");
            assertThat(MDC.get("uri")).isEqualTo("/api/test");
            return null;
        }).given(filterChain).doFilter(any(), any());

        filter.doFilterInternal(request, response, filterChain);
    }

    @Test
    @DisplayName("응답 헤더에 X-Request-Id를 설정한다")
    void setsRequestIdResponseHeader() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("X-Request-Id")).isNotNull().hasSize(8);
    }

    @Test
    @DisplayName("필터 체인 완료 후 MDC를 정리한다")
    void clearsMdcAfterFilterChain() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("method")).isNull();
        assertThat(MDC.get("uri")).isNull();
    }

    @Test
    @DisplayName("필터 체인에서 예외가 발생해도 MDC를 정리한다")
    void clearsMdcOnException() throws ServletException, IOException {
        willAnswer(invocation -> {
            throw new ServletException("test error");
        }).given(filterChain).doFilter(any(), any());

        try {
            filter.doFilterInternal(request, response, filterChain);
        } catch (ServletException ignored) {
        }

        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("필터 체인을 실행한다")
    void invokesFilterChain() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
