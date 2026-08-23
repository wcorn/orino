package ds.project.orino;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
// 방문 기록이 리다이렉트를 막지 않도록 비동기로 돈다(명세 §6.5).
@EnableAsync
public class OrinoApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrinoApplication.class, args);
    }
}
