package ds.project.orino.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 프로덕션 {@code Clock.systemUTC()} 자리에 {@link TestClock}을 끼운다.
 *
 * <p>{@link IntegrationTest}가 항상 들고 들어간다 — 시각을 안 건드리는 테스트도 이 빈을
 * 쓰지만, 풀린 TestClock은 systemUTC와 동작이 같아서 달라지는 것이 없다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    @Bean
    @Primary
    public TestClock testClock() {
        return new TestClock();
    }
}
