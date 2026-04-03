package ds.project.orino.domain.support;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "ds.project.orino.domain")
@EntityScan(basePackages = "ds.project.orino.domain")
@EnableJpaRepositories(basePackages = "ds.project.orino.domain")
public class TestJpaConfig {
}
