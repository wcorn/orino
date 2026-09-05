package ds.project.orino;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan
// 스케줄링은 SchedulingConfig가 켠다 — 테스트에서 폴러가 스스로 돌지 않게 하려면
// 방아쇠를 프로필로 가를 수 있어야 한다.
// 방문 기록이 리다이렉트를 막지 않도록 비동기로 돈다(명세 §6.5).
@EnableAsync
public class OrinoApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrinoApplication.class, args);
    }
}
