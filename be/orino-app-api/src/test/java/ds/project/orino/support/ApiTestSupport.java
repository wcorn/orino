package ds.project.orino.support;

import ds.project.orino.common.time.StudyDay;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
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

    @Autowired
    private TestClock testClock;

    /**
     * {@link FixedClock}이 붙어 있으면 그 시각으로 못박고, 없으면 실시각으로 되돌린다.
     *
     * <p>되돌리는 쪽이 중요하다 — 컨텍스트가 한 벌이라 시계도 한 개고, 앞선 클래스가 못박아
     * 둔 시각이 그대로 남으면 다음 클래스가 엉뚱한 "지금"을 본다.
     *
     * <p>{@code @Nested} 안쪽 클래스는 자기 자신에 애너테이션이 없으면 바깥 클래스를 따라
     * 올라간다. 예전에 {@code @Import}가 중첩 클래스로 상속되던 것과 같은 규칙이다.
     */
    @BeforeEach
    void applyFixedClock() {
        FixedClock fixedClock = findFixedClock(getClass());
        if (fixedClock == null) {
            testClock.release();
        } else {
            testClock.fixAt(Instant.parse(fixedClock.value()));
        }
    }

    private static FixedClock findFixedClock(Class<?> testClass) {
        for (Class<?> type = testClass; type != null; type = type.getEnclosingClass()) {
            FixedClock found = AnnotationUtils.findAnnotation(type, FixedClock.class);
            if (found != null) {
                return found;
            }
        }
        return null;
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
