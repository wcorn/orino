package ds.project.orino.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@IntegrationTest
public abstract class ApiTestSupport {

    /** 테스트 기본 사용자 시간대. 모든 요청에 X-Timezone 헤더로 주입한다. */
    protected static final ZoneId TEST_ZONE = ZoneId.of("Asia/Seoul");

    protected MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").header("X-Timezone", TEST_ZONE.getId()))
                .build();
    }

    /** 사용자 시간대(Asia/Seoul) 로컬 시각을 UTC Instant로 변환한다. */
    protected static Instant atTestZone(LocalDateTime localDateTime) {
        return localDateTime.atZone(TEST_ZONE).toInstant();
    }

    /** 사용자 시간대(Asia/Seoul) 기준 오늘 날짜. */
    protected static LocalDate testToday(Clock clock) {
        return clock.instant().atZone(TEST_ZONE).toLocalDate();
    }
}
