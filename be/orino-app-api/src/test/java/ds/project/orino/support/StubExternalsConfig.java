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

/**
 * 외부 호출을 전부 스텁으로 갈아끼운 설정.
 *
 * <p><b>테스트마다 다른 스텁 조합을 쓰지 않는다.</b> Spring은 설정이 조금이라도 다르면 컨텍스트를
 * 새로 띄우고 캐시에 쌓아 둔다 — 조합이 늘수록 컨텍스트가 늘고, 각각이 EntityManagerFactory와
 * 커넥션 풀을 물고 있어 결국 {@code OutOfMemoryError}로 무너진다(실제로 겪었다).
 *
 * <p>필요한 스텁만 골라 쓰고 나머지는 그대로 두면 된다. 안 쓰는 스텁은 아무 일도 하지 않는다.
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
}
