package ds.project.orino.planner.lifelog.geocode.controller;

import ds.project.orino.planner.lifelog.geocode.GeocodePlace;
import ds.project.orino.planner.lifelog.geocode.service.GeocodingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 라우팅·파라미터 바인딩(q/lat/lng/limit)·응답 형태를 standalone MockMvc로 검증한다.
 * (Spring 컨텍스트/DB 없이 — 새 컨텍스트로 인한 커넥션 부담 회피)
 */
class GeocodingControllerTest {

    private GeocodingService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(GeocodingService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new GeocodingController(service)).build();
    }

    @Test
    @DisplayName("GET /reverse - lat·lng를 double로 바인딩하고 place를 반환한다")
    void reverse() throws Exception {
        when(service.reverse(33.458, 126.942)).thenReturn(
                new GeocodePlace("성산일출봉", new BigDecimal("33.458"), new BigDecimal("126.942")));

        mockMvc.perform(get("/api/lifelog/geocode/reverse")
                        .param("lat", "33.458").param("lng", "126.942"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.placeName").value("성산일출봉"));

        verify(service).reverse(33.458, 126.942);
    }

    @Test
    @DisplayName("GET /search - q를 바인딩하고 limit 미지정은 null로 위임한다")
    void searchWithoutLimit() throws Exception {
        when(service.search(eq("성산"), isNull())).thenReturn(List.of(
                new GeocodePlace("성산일출봉", new BigDecimal("33.458"), new BigDecimal("126.942"))));

        mockMvc.perform(get("/api/lifelog/geocode/search").param("q", "성산"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].placeName").value("성산일출봉"));

        verify(service).search("성산", null);
    }

    @Test
    @DisplayName("GET /search - limit을 Integer로 바인딩한다")
    void searchWithLimit() throws Exception {
        when(service.search(eq("제주"), eq(3))).thenReturn(List.of());

        mockMvc.perform(get("/api/lifelog/geocode/search").param("q", "제주").param("limit", "3"))
                .andExpect(status().isOk());

        verify(service).search("제주", 3);
    }
}
