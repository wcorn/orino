package ds.project.orino.config;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestRedisConfig {

    /**
     * JVM 하나에 Redis 하나. 컨텍스트마다 새로 띄우지 않는다.
     *
     * <p>이 설정은 {@link ds.project.orino.support.IntegrationTest}가 들고 들어가므로 모든
     * 통합 테스트 컨텍스트에 붙는다. 컨테이너를 {@code @Bean} 안에서 새로 만들면 캐시된
     * 컨텍스트 수만큼 Redis가 뜬다 — 테스트를 돌리며 세어 보니 포크당 아홉 개까지 갔다.
     * MySQL은 {@code jdbc:tc:} URL이라 이미 JVM당 하나를 공유하고 있었고, Redis만 빠져 있었다.
     *
     * <p>이 인스턴스를 그대로 돌려줘도 되는 이유가 둘 있다. {@code GenericContainer.start()}는
     * 이미 뜬 컨테이너에 대해 즉시 반환하고, 스프링 부트는 {@code Startable} 빈의 추론된
     * destroy 메서드를 지워 두기 때문에({@code TestcontainersLifecycleBeanFactoryPostProcessor})
     * 먼저 닫히는 컨텍스트가 뒤에 올 컨텍스트의 Redis를 멈춰 버리지 않는다.
     */
    private static final RedisContainer REDIS = new RedisContainer("redis:7");

    @Bean
    @ServiceConnection
    public RedisContainer redisContainer() {
        return REDIS;
    }
}
