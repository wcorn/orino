package ds.project.orino;

import ds.project.orino.core.security.ActuatorSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ConfigurationPropertiesScan
@ComponentScan(excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE, classes = ActuatorSecurityConfig.class))
public class OrinoApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrinoApplication.class, args);
    }
}
