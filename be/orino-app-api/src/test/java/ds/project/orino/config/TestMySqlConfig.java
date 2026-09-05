package ds.project.orino.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * JVM 하나에 MySQL 하나. {@link TestRedisConfig}와 같은 방식이다.
 *
 * <p>한때 이 자리는 {@code jdbc:tc:mysql:...} URL이었다. 컨테이너를 JVM당 하나로 공유하는
 * 목적은 같았지만, 그 URL을 처리하는 {@code ContainerDatabaseDriver}는 <b>JVM 셧다운 훅에서
 * 컨테이너를 직접 멈춘다.</b> 스프링이 컨텍스트를 닫는 것도 셧다운 훅이라 둘 사이에 순서
 * 보장이 없고, 컨테이너가 먼저 죽으면 아직 닫히는 중인 커넥션 풀과 EntityManagerFactory가
 * 죽은 연결 위에서 정리 작업을 하게 된다.
 *
 * <p>컨테이너 빈에는 그 훅이 없다. 스프링 부트는 {@code Startable} 빈의 destroy 메서드가
 * 추론값일 때 그것을 지우고({@code TestcontainersLifecycleBeanFactoryPostProcessor}),
 * 테스트컨테이너는 정리를 Ryuk에 맡긴다 — Ryuk은 JVM이 끝난 <b>뒤에</b> 컨테이너를 지운다.
 * 그래서 종료가 「스프링 컨텍스트 → JVM 종료 → 컨테이너」 한 방향이 된다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestMySqlConfig {

    private static final MySQLContainer MYSQL =
            new MySQLContainer(DockerImageName.parse("mysql:8.4.4"));

    @Bean
    @ServiceConnection
    public MySQLContainer mysqlContainer() {
        return MYSQL;
    }
}
