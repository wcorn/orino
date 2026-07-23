package ds.project.orino.planner.lifelog.geocode.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.planner.lifelog.geocode.GeocodePlace;
import ds.project.orino.planner.lifelog.geocode.client.GeocodingClient;
import ds.project.orino.planner.lifelog.geocode.config.NominatimProperties;
import ds.project.orino.redis.planner.lifelog.GeocodeCacheRepository;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 지오코딩 오케스트레이션: Redis 캐시 우선 조회 → 미스면 {@link GeocodingClient} 호출 → 캐시 저장.
 *
 * <p>외부 호출 실패는 {@link ErrorCode#LIFELOG_GEOCODING_FAILED}로 변환한다. 이 실패는 장소명 표시
 * 편의 기능일 뿐이라, 클라이언트(FE)는 위치 없이도 기록을 저장할 수 있다(graceful degradation).
 */
@Service
public class GeocodingService {

    /** 좌표 캐시 키 반올림 자릿수(4 ≈ 11m). */
    private static final int COORD_SCALE = 4;
    private static final int MAX_SEARCH_LIMIT = 10;
    private static final int DEFAULT_SEARCH_LIMIT = 5;

    private static final TypeReference<List<GeocodePlace>> PLACE_LIST = new TypeReference<>() {
    };

    private final GeocodingClient client;
    private final GeocodeCacheRepository cache;
    private final NominatimProperties props;
    private final ObjectMapper objectMapper;

    public GeocodingService(GeocodingClient client, GeocodeCacheRepository cache,
                            NominatimProperties props, ObjectMapper objectMapper) {
        this.client = client;
        this.cache = cache;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * 좌표 → 장소. 결과가 없으면 장소명 null로 입력 좌표를 그대로 돌려준다(FE가 좌표만 저장 가능).
     */
    public GeocodePlace reverse(double lat, double lng) {
        String latKey = round(lat);
        String lngKey = round(lng);

        Optional<String> cached = cache.findReverse(latKey, lngKey);
        if (cached.isPresent()) {
            return objectMapper.readValue(cached.get(), GeocodePlace.class);
        }

        GeocodePlace place;
        try {
            place = client.reverse(lat, lng).orElse(null);
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.LIFELOG_GEOCODING_FAILED, e);
        }
        if (place == null) {
            // 결과 없음도 캐시해 같은 좌표 반복 조회를 흡수한다.
            place = new GeocodePlace(null, new BigDecimal(latKey), new BigDecimal(lngKey));
        }
        cache.saveReverse(latKey, lngKey, objectMapper.writeValueAsString(place), props.reverseTtl());
        return place;
    }

    /** 검색어 → 후보 장소. limit은 [1, 10]으로 보정한다. */
    public List<GeocodePlace> search(String query, Integer limit) {
        int size = clampLimit(limit);
        String normalized = query.trim().toLowerCase(Locale.ROOT);

        Optional<String> cached = cache.findSearch(normalized, size);
        if (cached.isPresent()) {
            return objectMapper.readValue(cached.get(), PLACE_LIST);
        }

        List<GeocodePlace> results;
        try {
            results = client.search(query.trim(), size);
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.LIFELOG_GEOCODING_FAILED, e);
        }
        cache.saveSearch(normalized, size, objectMapper.writeValueAsString(results), props.searchTtl());
        return results;
    }

    private String round(double coord) {
        return BigDecimal.valueOf(coord).setScale(COORD_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    private int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_SEARCH_LIMIT;
        }
        return Math.max(1, Math.min(MAX_SEARCH_LIMIT, limit));
    }
}
