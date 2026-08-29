package ds.project.orino.redis.support;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestRedisConfig {

    /**
     * JVM 하나에 Redis 하나. 컨텍스트마다 새로 띄우지 않는다.
     *
     * <p>{@code @Bean} 안에서 새로 만들면 캐시된 컨텍스트 수만큼 Redis가 뜬다.
     * {@code GenericContainer.start()}는 이미 뜬 컨테이너에 대해 즉시 반환하고, 스프링 부트는
     * {@code Startable} 빈의 추론된 destroy 메서드를 지우므로, 이 인스턴스를 여러 컨텍스트가
     * 나눠 써도 먼저 닫히는 쪽이 뒤에 올 컨텍스트의 Redis를 멈추지 않는다.
     */
    private static final RedisContainer REDIS = new RedisContainer("redis:7");

    @Bean
    @ServiceConnection
    public RedisContainer redisContainer() {
        return REDIS;
    }
}
