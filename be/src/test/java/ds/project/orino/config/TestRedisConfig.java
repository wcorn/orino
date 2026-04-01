package ds.project.orino.config;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestRedisConfig {

    @Bean
    @ServiceConnection
    public RedisContainer redisContainer() {
        return new RedisContainer("redis:7");
    }
}
