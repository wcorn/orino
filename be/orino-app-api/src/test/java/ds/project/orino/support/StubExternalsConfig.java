package ds.project.orino.support;

import ds.project.orino.planner.travel.place.StubPlacesClient;
import ds.project.orino.planner.travel.place.client.PlacesClient;
import ds.project.orino.planner.travel.push.StubWebPushSender;
import ds.project.orino.planner.travel.push.send.WebPushSender;
import ds.project.orino.planner.travel.tools.StubEcbRatesClient;
import ds.project.orino.planner.travel.tools.StubWeatherClient;
import ds.project.orino.planner.travel.tools.client.EcbRatesClient;
import ds.project.orino.planner.travel.tools.client.WeatherClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 외부 호출을 전부 스텁으로 갈아끼운 설정. <b>{@link IntegrationTest}가 들고 들어간다</b> —
 * 테스트가 직접 {@code @Import} 하지 않는다.
 *
 * <p><b>테스트마다 다른 스텁 조합을 쓰지 않는다.</b> Spring은 설정이 조금이라도 다르면 컨텍스트를
 * 새로 띄우고 캐시에 쌓아 둔다 — 조합이 늘수록 컨텍스트가 늘고, 각각이 EntityManagerFactory와
 * 커넥션 풀을 물고 있어 결국 {@code OutOfMemoryError}로 무너진다(실제로 겪었다).
 * 그래서 조합을 고르게 두지 않고 <b>한 벌로 못박았다</b>(#1287).
 *
 * <p>안 쓰는 스텁은 아무 일도 하지 않는다. 시계도 마찬가지다 — 못박지 않으면 실시각으로 돈다.
 */
@TestConfiguration
public class StubExternalsConfig {

    @Bean
    @Primary
    public PlacesClient stubPlacesClient() {
        return new StubPlacesClient();
    }

    @Bean
    @Primary
    public WebPushSender stubWebPushSender() {
        return new StubWebPushSender();
    }

    @Bean
    @Primary
    public WeatherClient stubWeatherClient() {
        return new StubWeatherClient();
    }

    @Bean
    @Primary
    public EcbRatesClient stubEcbRatesClient() {
        return new StubEcbRatesClient();
    }

    /** 영수증 보존 배치가 훑는 버킷. 메모리 위에 두고 목록·삭제만 흉내 낸다(#1275). */
    @Bean
    @Primary
    public S3Client stubImageS3Client() {
        return new StubS3Client();
    }

    /**
     * 갈아끼울 수 있는 시계. <b>시각을 못박는 일이 컨텍스트를 가르지 않게</b> 한다(#1287).
     *
     * <p>{@link ApiTestSupport}가 테스트마다 실시각으로 되돌리고, 못박을 테스트는
     * {@code fixedNow()}를 재정의한다.
     */
    @Bean
    @Primary
    public MutableTestClock testClock() {
        return new MutableTestClock();
    }
}
