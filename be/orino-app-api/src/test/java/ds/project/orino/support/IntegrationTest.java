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
 * 통합 테스트의 스프링 설정. <b>여기 있는 것이 전부이고, 테스트가 여기에 더 얹지 않는다.</b>
 *
 * <p>스프링은 설정이 조금이라도 다르면 컨텍스트를 새로 띄워 캐시에 쌓아 둔다. 테스트마다
 * {@code @Import}로 조합을 달리하면 그 조합 수만큼 컨텍스트가 생기고, 각각이
 * EntityManagerFactory와 커넥션 풀과 테스트컨테이너를 물고 있다 — 한때 JVM당 일곱 벌까지
 * 갔고 그만큼이 매 CI 실행의 기동 비용이었다.
 *
 * <p>그래서 갈릴 만한 것을 전부 이 한 벌에 접어 넣었다. 외부 호출 스텁은 안 쓰면 아무 일도
 * 하지 않고({@link StubExternalsConfig}), 시각은 설정이 아니라 값으로 갈아끼운다
 * ({@link FixedClock}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import({StubExternalsConfig.class, TestRedisConfig.class, TestClockConfig.class})
public @interface IntegrationTest {
}
