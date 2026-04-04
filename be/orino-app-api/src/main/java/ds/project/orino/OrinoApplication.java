package ds.project.orino;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OrinoApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrinoApplication.class, args);
    }
}
