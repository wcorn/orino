package ds.project.orino.planner.ledger;

import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.planner.travel.tools.StubEcbRatesClient;
import ds.project.orino.planner.travel.tools.client.EcbRates;
import ds.project.orino.planner.travel.tools.client.EcbRatesClient;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.StubExternalsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 외화(#1259 · D-9).
 *
 * <p>이 테스트가 지키는 한 문장: <b>환율은 거래 시점에 고정되고 다시는 재계산되지 않는다.</b>
 * 재계산하면 과거 지출액이 매일 바뀌고, 그러면 「지난달 얼마 썼나」에 답할 수 없다.
 * 원장은 사실의 기록이지 시세 평가가 아니다.
 *
 * <p>고시표 캐시는 통화쌍과 무관한 전역 키라 테스트 사이에 샌다. 매번 지우고 시작한다.
 */
@Import(StubExternalsConfig.class)
class LedgerFxTest extends ApiTestSupport {

    private static final String FX_CACHE_KEY = "travel:fx:ecb";

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private EcbRatesClient ratesClient;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private StubEcbRatesClient ratesStub;
    private String authHeader;
    private long checking;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        redisTemplate.delete(FX_CACHE_KEY);
        ratesStub = (StubEcbRatesClient) ratesClient;
        ratesStub.reset();

        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        checking = LedgerFixture.createAsset(mockMvc, authHeader, "급여통장", "CHECKING");
    }

    @Test
    @DisplayName("환율을 안 보내면 고시로 채우고 그 값을 거래에 고정한다")
    void fillsRateFromEcb() throws Exception {
        // 스텁 고시표: 1 EUR = 182.64 JPY = 1600.00 KRW → 1 JPY ≈ 8.7604 KRW
        String body = LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "occurredOn": "%s", "assetId": %d,
                 "title": "이치란 라멘",
                 "fx": {"currency": "JPY", "amount": 1280.00}}
                """.formatted(LocalDate.now(TEST_ZONE), checking));

        mockMvc.perform(get("/api/ledger/transactions/"
                        + LedgerFixture.transactionId(body))
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                // round(1280.00 × 8.7604) = 11,213
                .andExpect(jsonPath("$.data.amount").value(11213))
                .andExpect(jsonPath("$.data.fx.currency").value("JPY"))
                .andExpect(jsonPath("$.data.fx.rate").value(8.7604));
    }

    @Test
    @DisplayName("고시표가 갱신돼도 과거 거래의 원화 금액은 바뀌지 않는다")
    void pastAmountsNeverRecalculate() throws Exception {
        long id = LedgerFixture.transactionId(LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "occurredOn": "%s", "assetId": %d,
                 "fx": {"currency": "JPY", "amount": 1000.00}}
                """.formatted(LocalDate.now(TEST_ZONE), checking)));

        // 엔화가 하루 사이에 크게 움직였다고 치자.
        redisTemplate.delete(FX_CACHE_KEY);
        ratesStub.result = Optional.of(new EcbRates(
                LocalDate.parse("2026-08-08"),
                Map.of("JPY", new BigDecimal("150.00"), "KRW", new BigDecimal("1600.00"))));

        // 새 조회는 새 환율을 준다.
        mockMvc.perform(get("/api/ledger/fx/rate")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("currency", "JPY"))
                .andExpect(jsonPath("$.data.rate").value(10.6667));

        // 그런데 이미 적힌 거래는 그대로다.
        mockMvc.perform(get("/api/ledger/transactions/" + id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.amount").value(8760))
                .andExpect(jsonPath("$.data.fx.rate").value(8.7604));
    }

    @Test
    @DisplayName("거래를 고치면 그때는 다시 계산한다 — 카드사 환율이 찍힌 뒤의 정상 경로다")
    void recalculatesOnUpdate() throws Exception {
        long id = LedgerFixture.transactionId(LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "occurredOn": "%s", "assetId": %d,
                 "fx": {"currency": "JPY", "amount": 1000.00}}
                """.formatted(LocalDate.now(TEST_ZONE), checking)));

        mockMvc.perform(patch("/api/ledger/transactions/" + id)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fx": {"currency": "JPY", "amount": 1000.00, "rate": 9.500000}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(9500))
                .andExpect(jsonPath("$.data.fx.rate").value(9.5));
    }

    @Test
    @DisplayName("ECB에 닿지 못해도 원화 금액이 있으면 저장된다 — 기록을 막지 않는다")
    void savesWithoutEcb() throws Exception {
        redisTemplate.delete(FX_CACHE_KEY);
        ratesStub.result = Optional.empty();

        String body = LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": 12000, "occurredOn": "%s", "assetId": %d,
                 "fx": {"currency": "JPY", "amount": 1280.00}}
                """.formatted(LocalDate.now(TEST_ZONE), checking));

        mockMvc.perform(get("/api/ledger/transactions/" + LedgerFixture.transactionId(body))
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                // 원화로만 남는다. 근거가 반쪽인 채로 fx를 적어 두지 않는다.
                .andExpect(jsonPath("$.data.amount").value(12000))
                .andExpect(jsonPath("$.data.fx").doesNotExist());
    }

    @Test
    @DisplayName("조회도 실패를 에러로 올리지 않는다 — rate가 비어서 올 뿐이다")
    void lookupReturnsNullRateWhenUnavailable() throws Exception {
        redisTemplate.delete(FX_CACHE_KEY);
        ratesStub.result = Optional.empty();

        mockMvc.perform(get("/api/ledger/fx/rate")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("currency", "JPY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rate").doesNotExist());
    }

    @Test
    @DisplayName("통화만 보내고 금액이 없으면 거부한다 — 반쪽 근거는 검증할 수 없다")
    void rejectsIncompleteFx() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/ledger/transactions")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "EXPENSE", "occurredOn": "%s", "assetId": %d,
                                 "fx": {"currency": "JPY"}}
                                """.formatted(LocalDate.now(TEST_ZONE), checking)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LDG-ERR-021"));
    }

    @Test
    @DisplayName("고시에 없는 통화는 거부한다 — 값이 없는 것과 서비스가 죽은 것은 다르다")
    void rejectsUnsupportedCurrency() throws Exception {
        mockMvc.perform(get("/api/ledger/fx/rate")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("currency", "XYZ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LDG-ERR-020"));
    }

    @Test
    @DisplayName("잔액과 합계는 원화 환산액만 읽는다 — 외화 숫자가 새어 들어가지 않는다")
    void aggregatesUseKrwOnly() throws Exception {
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "occurredOn": "%s", "assetId": %d,
                 "fx": {"currency": "JPY", "amount": 1000.00}}
                """.formatted(LocalDate.now(TEST_ZONE), checking));

        mockMvc.perform(get("/api/ledger/transactions")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                // 1,000(엔)이 아니라 8,760(원)이다.
                .andExpect(jsonPath("$.data.monthTotals.expense").value(8760));

        mockMvc.perform(get("/api/ledger/assets")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.totalAssets").value(-8760));
    }
}
