package ds.project.orino.support;

import ds.project.orino.common.time.StudyDay;
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

    /**
     * 앱이 "오늘"로 보는 학습일(Asia/Seoul, 04시 롤오버).
     *
     * <p>달력 날짜를 쓰면 자정~04:00 사이에 돌릴 때만 하루 어긋나 테스트가 깨진다 —
     * 그 시간대엔 앱이 아직 전날을 오늘로 보기 때문이다({@link StudyDay}).
     */
    protected static LocalDate testToday(Clock clock) {
        return StudyDay.of(clock.instant(), TEST_ZONE);
    }
}
