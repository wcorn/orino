package ds.project.orino.planner.ledger;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.TravelCityFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 거래에 여행을 붙인다(여행 v2.2 §3 · §18).
 *
 * <p><b>여행 안에 장부를 만들지 않는다.</b> 원장은 가계부 하나뿐이고 여행 화면은 그 위의
 * 읽기 뷰라, 가계부에 생기는 것은 컬럼 하나와 필터 몇 개다.
 *
 * <p>이 파일이 지키는 것 중 가장 중요한 하나는 <b>여행을 지워도 쓴 돈은 남는다</b>는 것이다.
 * 애플리케이션이 아니라 DB(FK {@code ON DELETE SET NULL})가 보장하므로, 여행을 지우는 길이
 * 하나 더 생겨도 원장에 죽은 참조가 남지 않는다(D-27).
 */
class LedgerTripLinkTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private LedgerTransactionRepository transactionRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;
    private String otherAuthHeader;
    private long checking;
    private long tripId;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        memberRepository.save(MemberFixture.create("other", "password"));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        otherAuthHeader = "Bearer "
                + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");

        checking = LedgerFixture.createAsset(mockMvc, authHeader, "급여통장", "CHECKING");
        tripId = createTrip(authHeader, "일본 가을");
    }

    @Nested
    @DisplayName("붙이기")
    class Attach {

        @Test
        @DisplayName("거래를 만들 때 여행을 붙인다")
        void attachesOnCreate() throws Exception {
            long id = LedgerFixture.transactionId(expense(4500, tripId));

            assertThat(transactionRepository.findById(id).orElseThrow().getTripId())
                    .isEqualTo(tripId);
        }

        @Test
        @DisplayName("없는 여행·남의 여행은 404 — 원장에 죽은 참조를 넣지 않는다")
        void rejectsUnknownTrip() throws Exception {
            long othersTrip = createTrip(otherAuthHeader, "남의 여행");

            mockMvc.perform(post("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(expenseBody(4500, othersTrip)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-001"));
        }

        @Test
        @DisplayName("고른 여러 건을 한 번에 붙인다 — 돌아와서 기간으로 걸러 한 번(§18)")
        void bulkAttaches() throws Exception {
            long first = LedgerFixture.transactionId(expense(4500, null));
            long second = LedgerFixture.transactionId(expense(12000, null));

            mockMvc.perform(post("/api/ledger/transactions/bulk")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"action": "ATTACH_TRIP", "ids": [%d, %d], "tripId": %d}
                                    """.formatted(first, second, tripId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.affected").value(2));

            assertThat(transactionRepository.findById(first).orElseThrow().getTripId())
                    .isEqualTo(tripId);
            assertThat(transactionRepository.findById(second).orElseThrow().getTripId())
                    .isEqualTo(tripId);
        }

        @Test
        @DisplayName("tripId를 비워 보내면 연결을 끊는다 — 「해제」를 따로 만들지 않는다")
        void bulkDetaches() throws Exception {
            long id = LedgerFixture.transactionId(expense(4500, tripId));

            mockMvc.perform(post("/api/ledger/transactions/bulk")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"action": "ATTACH_TRIP", "ids": [%d]}
                                    """.formatted(id)))
                    .andExpect(status().isOk());

            assertThat(transactionRepository.findById(id).orElseThrow().getTripId()).isNull();
        }

        @Test
        @DisplayName("일괄 붙이기도 남의 여행은 404다")
        void bulkRejectsUnknownTrip() throws Exception {
            long id = LedgerFixture.transactionId(expense(4500, null));
            long othersTrip = createTrip(otherAuthHeader, "남의 여행");

            mockMvc.perform(post("/api/ledger/transactions/bulk")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"action": "ATTACH_TRIP", "ids": [%d], "tripId": %d}
                                    """.formatted(id, othersTrip)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-001"));
        }
    }

    @Nested
    @DisplayName("여행을 지워도 쓴 돈은 남는다")
    class TripDeletion {

        @Test
        @DisplayName("연결만 끊기고 거래는 그대로다 — DB가 보장한다(ON DELETE SET NULL)")
        void keepsTransactionWhenTripDeleted() throws Exception {
            long id = LedgerFixture.transactionId(expense(4500, tripId));

            mockMvc.perform(delete("/api/travel/trips/" + tripId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            // 준비 항목(CASCADE)과 정반대다. 3만 원을 쓴 것은 여행 밖에서도 사실이다.
            assertThat(transactionRepository.findById(id)).isPresent()
                    .get()
                    .satisfies(tx -> {
                        assertThat(tx.getTripId()).isNull();
                        assertThat(tx.getAmount()).isEqualTo(4500);
                    });
            mockMvc.perform(get("/api/ledger/transactions/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("여행으로 거르기")
    class Filtering {

        @Test
        @DisplayName("그 여행에 붙은 것만 준다 — 상단 합계도 함께 걸린다")
        void filtersByTrip() throws Exception {
            expense(4500, tripId);
            expense(12000, tripId);
            expense(30000, null);

            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("tripId", String.valueOf(tripId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.groups[0].items", hasSize(2)))
                    // 목록만 거르고 합계는 전체를 세면 어느 쪽이 맞는지 알 수 없다.
                    .andExpect(jsonPath("$.data.monthTotals.expense").value(16500));
        }

        @Test
        @DisplayName("excludeTrip은 어느 여행에도 안 붙은 것만 준다 — 「여행 빼고 얼마 썼나」")
        void excludesTripLinked() throws Exception {
            expense(4500, tripId);
            expense(30000, null);

            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("excludeTrip", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.groups[0].items", hasSize(1)))
                    .andExpect(jsonPath("$.data.monthTotals.expense").value(30000));
        }

        @Test
        @DisplayName("이체는 여행에 붙어 있어도 지출 합계에 들어가지 않는다")
        void transferNeverCountsAsTripSpending() throws Exception {
            long savings = LedgerFixture.createAsset(mockMvc, authHeader, "비상금", "SAVINGS");
            expense(4500, tripId);
            // 카드 대금 납부가 여행 경비로 새는 것을 막는 자리다(§4.2 · R-15).
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "TRANSFER", "amount": 500000, "occurredOn": "%s",
                     "assetId": %d, "counterAssetId": %d, "tripId": %d}
                    """.formatted(today(), checking, savings, tripId));

            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("tripId", String.valueOf(tripId)))
                    .andExpect(status().isOk())
                    // 줄은 보인다 — 붙였다는 사실은 감추지 않는다.
                    .andExpect(jsonPath("$.data.groups[0].items", hasSize(2)))
                    // 그러나 지출은 4500뿐이다.
                    .andExpect(jsonPath("$.data.monthTotals.expense").value(4500))
                    .andExpect(jsonPath("$.data.monthTotals.transfer").value(500000));
        }

        @Test
        @DisplayName("필터가 없으면 전부 준다 — 기존 화면의 길은 그대로다")
        void unfilteredIsUnchanged() throws Exception {
            expense(4500, tripId);
            expense(30000, null);

            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.groups[0].items", hasSize(2)))
                    .andExpect(jsonPath("$.data.monthTotals.expense").value(34500));
        }

        @Test
        @DisplayName("거래 한 줄에 붙은 여행이 함께 온다")
        void rowCarriesTripId() throws Exception {
            expense(4500, tripId);

            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.groups[0].items[0].tripId")
                            .value((int) tripId));
        }
    }

    // ---------------- helpers ----------------

    private long createTrip(String header, String title) throws Exception {
        long cityId = TravelCityFixture.createCity(mockMvc, header, "오사카",
                "Asia/Tokyo", "JPY");
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, header)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "startDate": "2026-10-24",
                                 "endDate": "2026-10-27", %s}
                                """.formatted(title,
                                TravelCityFixture.singleLeg(cityId, 4))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private String expense(long amount, Long trip) throws Exception {
        return LedgerFixture.createTransaction(mockMvc, authHeader, expenseBody(amount, trip));
    }

    private String expenseBody(long amount, Long trip) {
        String tripField = trip == null ? "" : ", \"tripId\": " + trip;
        return """
                {"type": "EXPENSE", "amount": %d, "occurredOn": "%s", "assetId": %d%s}
                """.formatted(amount, today(), checking, tripField);
    }

    private static String today() {
        return LocalDate.now().toString();
    }
}
