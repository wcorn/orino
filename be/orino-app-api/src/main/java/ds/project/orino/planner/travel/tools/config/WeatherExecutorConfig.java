package ds.project.orino.planner.travel.tools.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 예보 조회를 보드 요청 스레드 밖으로 빼는 실행기(#1357).
 *
 * <p>두 가지에 쓴다 — <b>도시별 호출을 동시에</b> 던지는 것과, <b>만료된 캐시를 뒤에서
 * 갱신</b>하는 것. 둘 다 외부 HTTP를 기다리는 블로킹 I/O라 가상 스레드가 맞다
 * (통합 피드가 같은 이유로 같은 처방을 썼다 — #544).
 *
 * <p>도시마다 마감시한을 따로 걸면 6도시 여행이 그만큼 곱해진다. 한 번에 던지고 <b>전체에
 * 마감시한 하나</b>를 걸어야 도시 수와 무관하게 상한이 선다.
 */
@Configuration
public class WeatherExecutorConfig {

    @Bean(destroyMethod = "close")
    public ExecutorService weatherExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
