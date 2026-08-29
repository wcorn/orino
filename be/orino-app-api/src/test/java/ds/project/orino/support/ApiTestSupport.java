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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@IntegrationTest
public abstract class ApiTestSupport {

    /** 테스트 기본 사용자 시간대. 모든 요청에 X-Timezone 헤더로 주입한다. */
    protected static final ZoneId TEST_ZONE = ZoneId.of("Asia/Seoul");

    protected MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MutableTestClock testClock;

    /**
     * 이 테스트가 못박을 시각. {@code null}이면 실시각으로 돈다.
     *
     * <p>시각을 못박으려고 {@code @TestConfiguration}을 따로 두지 않는다 — 설정이 갈리면
     * 스프링 컨텍스트가 한 벌 더 뜬다(#1287). 재정의해서 값만 주면 된다.
     */
    protected Instant fixedNow() {
        return null;
    }

    /**
     * 매 테스트 시작에 시계를 세운다. 부모의 {@code @BeforeEach}가 자식보다 <b>먼저</b> 돌므로
     * 자식 준비 코드는 이미 못박힌 시각을 본다.
     */
    @BeforeEach
    void applyTestClock() {
        Instant now = fixedNow();
        if (now == null) {
            // 앞 테스트가 못박아 둔 시각이 새지 않게 매번 되돌린다.
            testClock.reset();
        } else {
            testClock.set(now);
        }
    }

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
