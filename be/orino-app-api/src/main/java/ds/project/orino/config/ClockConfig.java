package ds.project.orino.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        // 저장/비교는 UTC Instant 기준. 사용자 로컬 기준 계산은 요청 시간대(UserTimeZone)로 변환한다.
        return Clock.systemUTC();
    }
}
