package ds.project.orino.planner.lifelog.geocode.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.planner.lifelog.geocode.GeocodePlace;
import ds.project.orino.planner.lifelog.geocode.client.GeocodingClient;
import ds.project.orino.planner.lifelog.geocode.config.NominatimProperties;
import ds.project.orino.redis.planner.lifelog.GeocodeCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 지오코딩 오케스트레이션(캐시 히트/미스, 클라이언트 스텁, 실패 변환, limit 보정)을 고정한다.
 * 외부 네트워크·Redis·Spring 컨텍스트 없이 순수 로직만 검증한다.
 */
class GeocodingServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NominatimProperties props = new NominatimProperties(
            "https://nominatim.test", "orino-test", "ko",
            Duration.ofSeconds(1), Duration.ofDays(30), Duration.ofDays(7),
            Duration.ofSeconds(5), Duration.ofSeconds(10));

    private GeocodingClient client;
    private GeocodeCacheRepository cache;
    private GeocodingService service;

    @BeforeEach
    void setUp() {
        client = mock(GeocodingClient.class);
        cache = mock(GeocodeCacheRepository.class);
        service = new GeocodingService(client, cache, props, objectMapper);
    }

    @Test
    @DisplayName("reverse 캐시 히트 - 클라이언트를 호출하지 않고 캐시값을 돌려준다")
    void reverseCacheHit() {
        GeocodePlace cached = new GeocodePlace("성산일출봉",
                new BigDecimal("33.4580"), new BigDecimal("126.9420"));
        when(cache.findReverse("33.4580", "126.9420"))
                .thenReturn(Optional.of(objectMapper.writeValueAsString(cached)));

        GeocodePlace result = service.reverse(33.4580, 126.9420);

        assertThat(result.placeName()).isEqualTo("성산일출봉");
        verify(client, never()).reverse(anyDouble(), anyDouble());
        verify(cache, never()).saveReverse(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("reverse 캐시 미스 - 클라이언트 호출 후 결과를 캐시에 저장한다")
    void reverseCacheMiss() {
        when(cache.findReverse(anyString(), anyString())).thenReturn(Optional.empty());
        when(client.reverse(33.4580, 126.9420)).thenReturn(Optional.of(
                new GeocodePlace("성산일출봉", new BigDecimal("33.4580"), new BigDecimal("126.9420"))));

        GeocodePlace result = service.reverse(33.4580, 126.9420);

        assertThat(result.placeName()).isEqualTo("성산일출봉");
        verify(cache).saveReverse(eq("33.4580"), eq("126.9420"), anyString(), eq(props.reverseTtl()));
    }

    @Test
    @DisplayName("reverse 결과 없음 - 장소명 null로 반올림 좌표를 돌려주고 캐시한다")
    void reverseNotFound() {
        when(cache.findReverse(anyString(), anyString())).thenReturn(Optional.empty());
        when(client.reverse(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        GeocodePlace result = service.reverse(33.456789, 126.942111);

        assertThat(result.placeName()).isNull();
        assertThat(result.lat()).isEqualByComparingTo("33.4568");
        assertThat(result.lng()).isEqualByComparingTo("126.9421");
        verify(cache).saveReverse(eq("33.4568"), eq("126.9421"), anyString(), any());
    }

    @Test
    @DisplayName("reverse 클라이언트 실패 - LIFELOG_GEOCODING_FAILED로 변환한다")
    void reverseClientFailure() {
        when(cache.findReverse(anyString(), anyString())).thenReturn(Optional.empty());
        when(client.reverse(anyDouble(), anyDouble())).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.reverse(33.45, 126.94))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LIFELOG_GEOCODING_FAILED);
    }

    @Test
    @DisplayName("search 캐시 히트 - 캐시된 목록을 역직렬화해 돌려준다")
    void searchCacheHit() {
        List<GeocodePlace> cachedList = List.of(
                new GeocodePlace("성산일출봉", new BigDecimal("33.458"), new BigDecimal("126.942")));
        when(cache.findSearch("성산", 5))
                .thenReturn(Optional.of(objectMapper.writeValueAsString(cachedList)));

        List<GeocodePlace> result = service.search("성산", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).placeName()).isEqualTo("성산일출봉");
        verify(client, never()).search(anyString(), anyInt());
    }

    @Test
    @DisplayName("search limit은 [1,10]으로 보정되고 미지정은 5")
    void searchClampsLimit() {
        when(cache.findSearch(anyString(), anyInt())).thenReturn(Optional.empty());
        when(client.search(anyString(), anyInt())).thenReturn(List.of());

        service.search("제주", 50);
        verify(client).search("제주", 10);

        service.search("부산", null);
        verify(client).search("부산", 5);
    }
}
