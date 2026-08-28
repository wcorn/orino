package ds.project.orino.planner.ledger;

import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.FixedClockConfig;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.StubExternalsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Clock;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 요약과 설정(#1259). v1이 답할 수 없는 값은 {@code null}로 남는다.
 *
 * <p><b>시계를 못박는다.</b> 이 클래스는 「이번 달 구간」을 단언하는데, 실시각을 쓰면
 * 월말에만 깨진다 — 예정으로 넣은 「오늘+3일」이 다음 달로 넘어가 구간 밖이 되기 때문이다.
 * 실제로 8월 29일에 그렇게 깨졌다. 날짜 경계를 보는 테스트는 날짜를 정해 두고 봐야 한다.
 */
@Import({StubExternalsConfig.class, FixedClockConfig.class})
class LedgerSummaryAndSettingsTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private Clock clock;

    private String authHeader;
    private long checking;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        checking = LedgerFixture.createAsset(mockMvc, authHeader, "급여통장", "CHECKING");
    }

    /** 고정 시계 기준 오늘(2026-01-15). 실시각을 쓰면 월말에만 깨진다. */
    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), TEST_ZONE);
    }

    @Test
    @DisplayName("v1.5에서 채워질 값은 0이 아니라 null이다 — 「없다」와 「모른다」는 다르다")
    void v15ValuesAreNull() throws Exception {
        mockMvc.perform(get("/api/ledger/summary")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthEndBalance").doesNotExist())
                .andExpect(jsonPath("$.data.remainingOutflow").doesNotExist())
                .andExpect(jsonPath("$.data.overdueCount").doesNotExist());
    }

    @Test
    @DisplayName("예상 지출은 이미 쓴 돈과 예정을 더한 값이다")
    void estimateAddsScheduled() throws Exception {
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": 120000, "assetId": %d, "occurredOn": "%s"}
                """.formatted(checking, today()));
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": 30000, "assetId": %d, "occurredOn": "%s"}
                """.formatted(checking, today().plusDays(3)));

        mockMvc.perform(get("/api/ledger/summary")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.monthSpent").value(120000))
                .andExpect(jsonPath("$.data.monthScheduled").value(30000))
                .andExpect(jsonPath("$.data.monthEstimate").value(150000));
    }

    @Test
    @DisplayName("미분류 건수를 센다 — 이체는 애초에 분류 대상이 아니라 빠진다")
    void countsUncategorizedExcludingTransfer() throws Exception {
        long savings = LedgerFixture.createAsset(mockMvc, authHeader, "비상금", "SAVINGS");
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": 4500, "assetId": %d, "occurredOn": "%s"}
                """.formatted(checking, today()));
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "TRANSFER", "amount": 100000, "assetId": %d,
                 "counterAssetId": %d, "occurredOn": "%s"}
                """.formatted(checking, savings, today()));

        mockMvc.perform(get("/api/ledger/summary")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.uncategorizedCount").value(1));
    }

    @Test
    @DisplayName("월 시작일을 25일로 두면 이번 달 구간이 25일에서 시작한다")
    void monthStartDayShiftsPeriod() throws Exception {
        mockMvc.perform(patch("/api/ledger/settings")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthStartDay\": 25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthStartDay").value(25));

        mockMvc.perform(get("/api/ledger/summary")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.period.start").value(
                        org.hamcrest.Matchers.endsWith("-25")));
    }

    @Test
    @DisplayName("29~31일 시작은 거부한다 — 2월에 없는 날짜다")
    void rejectsUnsafeMonthStartDay() throws Exception {
        mockMvc.perform(patch("/api/ledger/settings")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthStartDay\": 30}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("말일 시작(99)은 허용한다")
    void allowsLastDayOfMonth() throws Exception {
        mockMvc.perform(patch("/api/ledger/settings")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthStartDay\": 99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthStartDay").value(99));
    }

    @Test
    @DisplayName("기본 자산은 실제로 있는 자산이어야 한다")
    void rejectsUnknownDefaultAsset() throws Exception {
        mockMvc.perform(patch("/api/ledger/settings")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultAssetId\": 999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LDG-ERR-001"));
    }
}
