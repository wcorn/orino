package ds.project.orino.planner.google.calendar;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 통합 피드의 독립 소스(일정·할 일)를 동시에 호출하기 위한 실행기.
 *
 * <p>대부분이 외부 Google API 블로킹 I/O라 가상 스레드(Java 21+)가 적합하다. 직렬 합산되던 외부 호출을
 * 병렬화해 calendar 지연(#544)을 max로 단축한다.
 */
@Configuration
public class PlannerFeedExecutorConfig {

    @Bean(destroyMethod = "close")
    public ExecutorService plannerFeedExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
