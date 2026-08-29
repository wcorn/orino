package ds.project.orino.support;

import ds.project.orino.redis.planner.travel.ToolsCacheRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DbCleaner}가 <b>Redis까지</b> 비우는지 지킨다.
 *
 * <p>이 단언이 없으면 캐시가 새는 것은 조용히 일어난다 — 앞 테스트의 응답이 뒤 테스트에
 * 흘러들어 <b>외부 호출 횟수를 세는 단언만</b> 가끔 깨지고, 실패는 코드와 무관한 자리에서
 * 뜬다. 실제로 그렇게 CI가 빨개졌다(#1294).
 */
@IntegrationTest
class DbCleanerCacheTest {

    private static final String KEY = "35.6812,139.7671:Asia/Tokyo";

    @Autowired
    private DbCleaner dbCleaner;

    @Autowired
    private ToolsCacheRepository cacheRepository;

    @Test
    @DisplayName("테이블뿐 아니라 캐시도 비운다")
    void clearsCacheToo() {
        cacheRepository.saveWeather(KEY, "{}", Duration.ofHours(6));
        assertThat(cacheRepository.findWeather(KEY)).isPresent();

        dbCleaner.clean();

        assertThat(cacheRepository.findWeather(KEY)).isEmpty();
    }
}
