package ds.project.orino.support;

import ds.project.orino.config.TestRedisConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 통합 테스트의 <b>유일한 설정</b>.
 *
 * <p>외부 스텁과 시계를 여기서 함께 들고 간다 — 테스트마다 {@code @Import} 조합을 다르게 두면
 * 그 수만큼 스프링 컨텍스트가 늘어난다(#1287). 각 컨텍스트가 EntityManagerFactory와 커넥션
 * 풀을 물고 있어, 조합이 늘면 느려지는 정도가 아니라 결국 메모리로 무너진다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import({TestRedisConfig.class, StubExternalsConfig.class})
public @interface IntegrationTest {
}
