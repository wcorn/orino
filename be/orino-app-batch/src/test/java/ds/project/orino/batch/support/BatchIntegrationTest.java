package ds.project.orino.batch.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "batch.consecutive-missed-days.cron=-",
                "spring.task.scheduling.enabled=false"
        })
@ActiveProfiles("test")
public @interface BatchIntegrationTest {
}
