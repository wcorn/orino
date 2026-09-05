package ds.project.orino.planner.travel.expense;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.planner.ledger.LedgerFixture;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.FixedClock;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.TravelCityFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 경비 조회와 예산(명세 v2.2 §4~§5 · API §11).
 *
 * <p>이 화면은 <b>읽기 뷰</b>다 — 출처는 가계부 원장이고 여행은 그것을 여행의 문법으로 다시
 * 묶을 뿐이다. 그래서 여기서 지키는 것은 대부분 「무엇을 세고 무엇을 안 세나」다.
 *
 * <p>고정 시각은 {@code 2026-01-15}(도쿄 11:00). 여행 기간을 그 앞뒤로 놓아 예정·진행 중·완료
 * 셋을 만든다 — 상태에 따라 「하루 얼마 쓸 수 있나」와 「하루 평균」이 자리를 바꾼다.
 */
@FixedClock
class TripExpenseApiTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;
    private String otherAuthHeader;
    private long checking;
    private long osaka;
    private long kyoto;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        memberRepository.save(MemberFixture.create("other", "password"));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        otherAuthHeader = "Bearer "
                + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");

        checking = LedgerFixture.createAsset(mockMvc, authHeader, "급여통장", "CHECKING");
        osaka = TravelCityFixture.createCity(mockMvc, authHeader, "오사카", "Asia/Tokyo", "JPY");
        kyoto = TravelCityFixture.createCity(mockMvc, authHeader, "교토", "Asia/Tokyo", "JPY");
    }

    @Nested
    @DisplayName("무엇을 세나")
    class WhatCounts {

        @Test
        @DisplayName("카드 대금 납부는 합계에도 목록에도 없다 — 애초에 빠진다")
        void transferIsNotAnExpense() throws Exception {
            long tripId = ongoingTrip();
            long savings = LedgerFixture.createAsset(mockMvc, authHeader, "비상금", "SAVINGS");
            expense(tripId, 32000, "이자카야", "2026-01-15");
            // 합계에서만 빼면 목록에는 보이고 합계에는 없는 줄이 생겨 더 헷갈린다(§4.2).
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "TRANSFER", "amount": 500000, "occurredOn": "2026-01-15",
                     "assetId": %d, "counterAssetId": %d, "tripId": %d}
                    """.formatted(checking, savings, tripId));

            expenses(tripId)
                    .andExpect(jsonPath("$.data.totals.spent").value(32000))
                    .andExpect(jsonPath("$.data.groups[?(@.key == 'DAY-2')].rows[*]",
                            hasSize(1)));
        }

        @Test
        @DisplayName("예정 지출은 따로 센다 — 게이지 2층이 이 값이다")
        void scheduledIsCountedApart() throws Exception {
            long tripId = ongoingTrip();
            expense(tripId, 32000, "이자카야", "2026-01-15");
            // 미래 날짜는 예정으로 저장된다(가계부 §4.2).
            expense(tripId, 80000, "숙소 잔금", "2026-01-17");

            expenses(tripId)
                    .andExpect(jsonPath("$.data.totals.spent").value(32000))
                    .andExpect(jsonPath("$.data.totals.scheduled").value(80000));
        }

        @Test
        @DisplayName("카테고리가 빈 건수를 센다 — 「정리할 내역 N건」")
        void countsUncategorized() throws Exception {
            long tripId = ongoingTrip();
            expense(tripId, 11300, "점심 라멘", "2026-01-15");
            expense(tripId, 4500, "커피", "2026-01-15");

            expenses(tripId).andExpect(jsonPath("$.data.unsortedCount").value(2));
        }
    }

    @Nested
    @DisplayName("어떻게 묶나")
    class Grouping {

        @Test
        @DisplayName("출발 전 결제는 「출발 전」으로 묶인다 — 항공권을 빼면 총액이 설명되지 않는다")
        void groupsBeforeDeparture() throws Exception {
            long tripId = ongoingTrip();
            expense(tripId, 640000, "항공권", "2025-11-30");
            expense(tripId, 32000, "이자카야", "2026-01-15");

            expenses(tripId)
                    .andExpect(jsonPath("$.data.groups[0].key").value("BEFORE"))
                    .andExpect(jsonPath("$.data.groups[0].label").value("출발 전"))
                    .andExpect(jsonPath("$.data.groups[0].sum").value(640000));
        }

        @Test
        @DisplayName("돌아온 뒤 결제는 「다녀온 뒤」로 같은 자리에 붙는다")
        void groupsAfterReturn() throws Exception {
            long tripId = completedTrip();
            expense(tripId, 18000, "현상 인화", "2026-01-12");

            expenses(tripId)
                    .andExpect(jsonPath("$.data.groups[-1:].key").value("AFTER"))
                    .andExpect(jsonPath("$.data.groups[-1:].sum").value(18000));
        }

        @Test
        @DisplayName("비어 있으면 출발 전·다녀온 뒤는 아예 내리지 않는다")
        void skipsEmptyEdgeGroups() throws Exception {
            long tripId = ongoingTrip();
            expense(tripId, 32000, "이자카야", "2026-01-15");

            // 늘 보이면 여행 중 화면의 위아래가 빈 카드로 찬다.
            expenses(tripId)
                    .andExpect(jsonPath("$.data.groups[?(@.key == 'BEFORE')]", hasSize(0)))
                    .andExpect(jsonPath("$.data.groups[?(@.key == 'AFTER')]", hasSize(0)))
                    .andExpect(jsonPath("$.data.groups", hasSize(3)));
        }

        @Test
        @DisplayName("지출이 없는 날짜도 sum 0으로 내려간다 — 화면이 「아직 없어요」를 그린다")
        void keepsEmptyDayGroups() throws Exception {
            long tripId = ongoingTrip();

            expenses(tripId)
                    .andExpect(jsonPath("$.data.groups", hasSize(3)))
                    .andExpect(jsonPath("$.data.groups[0].key").value("DAY-1"))
                    .andExpect(jsonPath("$.data.groups[0].sum").value(0))
                    .andExpect(jsonPath("$.data.groups[0].rows", hasSize(0)));
        }

        @Test
        @DisplayName("라벨은 저장하지 않는다 — 기준 도시를 바꾸면 따라 움직인다")
        void labelFollowsBaseCity() throws Exception {
            long tripId = ongoingTrip();
            expense(tripId, 32000, "이자카야", "2026-01-15");

            expenses(tripId)
                    .andExpect(jsonPath("$.data.groups[1].label").value("2일차 · 오사카"));

            changeBaseCity(dayIdOf(tripId, 1), kyoto);

            // 저장했다면 옛 도시가 조용히 남았을 자리다.
            expenses(tripId)
                    .andExpect(jsonPath("$.data.groups[1].label").value("2일차 · 교토"))
                    .andExpect(jsonPath("$.data.groups[1].cityName").value("교토"));
        }
    }

    @Nested
    @DisplayName("예산")
    class Budget {

        @Test
        @DisplayName("안 정했으면 budget이 통째로 null이다 — 0을 내리지 않는다")
        void nullWhenUnset() throws Exception {
            long tripId = ongoingTrip();
            expense(tripId, 32000, "이자카야", "2026-01-15");

            // amount: 0을 내리면 화면이 「0원 중 3.2만」을 그린다(§5.3).
            expenses(tripId)
                    .andExpect(jsonPath("$.data.budget").value(nullValue()))
                    // 「얼마 썼나」는 예산 없이도 답이 있다.
                    .andExpect(jsonPath("$.data.totals.spent").value(32000));
        }

        @Test
        @DisplayName("정하면 남은 돈과 하루 쓸 수 있는 돈이 함께 온다")
        void derivesDailyAllowance() throws Exception {
            long tripId = ongoingTrip();
            putBudget(tripId, "800000").andExpect(jsonPath("$.data.amount").value(800000));
            expense(tripId, 200000, "이자카야", "2026-01-15");

            // 오늘이 2일차, 기간은 1/14~1/16이라 남은 날은 오늘 포함 2일이다.
            expenses(tripId)
                    .andExpect(jsonPath("$.data.budget.amount").value(800000))
                    .andExpect(jsonPath("$.data.budget.spent").value(200000))
                    .andExpect(jsonPath("$.data.budget.remaining").value(600000))
                    .andExpect(jsonPath("$.data.budget.daysLeft").value(2))
                    .andExpect(jsonPath("$.data.budget.dailyAllowance").value(300000));
        }

        @Test
        @DisplayName("다녀온 뒤에는 하루 평균이 그 자리를 받는다 — 둘이 동시에 차지 않는다")
        void averageReplacesAllowanceAfterTrip() throws Exception {
            long tripId = completedTrip();
            putBudget(tripId, "800000");
            expense(tripId, 900000, "이자카야", "2026-01-06");

            expenses(tripId)
                    .andExpect(jsonPath("$.data.budget.dailyAllowance").value(nullValue()))
                    .andExpect(jsonPath("$.data.budget.daysLeft").value(nullValue()))
                    // 총 3일 · 90만이면 하루 평균 30만.
                    .andExpect(jsonPath("$.data.totals.dailyAverage").value(300000));
        }

        @Test
        @DisplayName("예산을 넘겨도 하루 쓸 수 있는 돈이 음수가 되지는 않는다")
        void allowanceNeverGoesNegative() throws Exception {
            long tripId = ongoingTrip();
            putBudget(tripId, "100000");
            expense(tripId, 300000, "이자카야", "2026-01-15");

            expenses(tripId)
                    // 남은 돈은 사실대로 음수다 — 초과했다는 것이 그 값의 내용이다.
                    .andExpect(jsonPath("$.data.budget.remaining").value(-200000))
                    // 그러나 「하루 −10만 쓸 수 있다」는 말이 안 된다.
                    .andExpect(jsonPath("$.data.budget.dailyAllowance").value(0));
        }

        @Test
        @DisplayName("0은 400이다 — 「안 정함」과 구분되지 않는다")
        void rejectsZero() throws Exception {
            long tripId = ongoingTrip();

            mockMvc.perform(put("/api/travel/trips/" + tripId + "/budget")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\": 0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-024"));
        }

        @Test
        @DisplayName("null이 해제다")
        void nullClearsBudget() throws Exception {
            long tripId = ongoingTrip();
            putBudget(tripId, "800000");

            putBudget(tripId, "null").andExpect(jsonPath("$.data.amount").value(nullValue()));

            expenses(tripId).andExpect(jsonPath("$.data.budget").value(nullValue()));
        }
    }

    /**
     * 사이드바 여행 트리의 경비 한 줄(#1345 · API §2.1). 여기서 지키는 것은 <b>화면과 같은
     * 값인가</b> 하나다 — 사이드바를 보고 들어간 사람이 경비 화면에서 다른 숫자를 보면,
     * 어느 쪽이 맞는지 알 방법이 없다.
     */
    @Nested
    @DisplayName("사이드바 요약 — trips[].expense")
    class SidebarExpense {

        @Test
        @DisplayName("사이드바의 spent가 경비 화면의 「썼다」와 같은 값이다")
        void spentMatchesExpenseScreen() throws Exception {
            long tripId = ongoingTrip();
            expense(tripId, 32000, "이자카야", "2026-01-15");
            expense(tripId, 11300, "점심 라멘", "2026-01-15");
            // 아직 안 나간 돈은 「썼다」가 아니다 — 화면도 여기도 예정은 빼고 센다.
            expense(tripId, 80000, "숙소 잔금", "2026-01-17");
            // 카드 대금 납부는 이체라 애초에 여행 경비가 아니다(§4.2).
            long savings = LedgerFixture.createAsset(mockMvc, authHeader, "비상금", "SAVINGS");
            LedgerFixture.createTransaction(mockMvc, authHeader, """
                    {"type": "TRANSFER", "amount": 500000, "occurredOn": "2026-01-15",
                     "assetId": %d, "counterAssetId": %d, "tripId": %d}
                    """.formatted(checking, savings, tripId));

            expenses(tripId).andExpect(jsonPath("$.data.totals.spent").value(43300));

            summary()
                    .andExpect(jsonPath("$.data.trips[0].expense.spent").value(43300));
        }

        @Test
        @DisplayName("예산을 정했으면 그대로, 안 정했으면 null이다 — 0을 내리지 않는다")
        void budgetIsNullWhenUnset() throws Exception {
            long tripId = ongoingTrip();

            summary().andExpect(jsonPath("$.data.trips[0].expense.budget").value(nullValue()));

            putBudget(tripId, "800000");

            summary().andExpect(jsonPath("$.data.trips[0].expense.budget").value(800000));
        }

        @Test
        @DisplayName("한 푼도 안 쓴 여행도 expense가 온다 — spent가 0이지 null이 아니다")
        void spentIsZeroNotNull() throws Exception {
            ongoingTrip();

            summary()
                    .andExpect(jsonPath("$.data.trips[0].expense").exists())
                    .andExpect(jsonPath("$.data.trips[0].expense.spent").value(0))
                    .andExpect(jsonPath("$.data.trips[0].expense.budget").value(nullValue()));
        }

        private ResultActions summary() throws Exception {
            return mockMvc.perform(get("/api/travel/summary")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("남의 여행은 404 — 조회도 예산도")
    void hidesOthersTrip() throws Exception {
        long tripId = ongoingTrip();

        mockMvc.perform(get("/api/travel/trips/" + tripId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, otherAuthHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRAVEL-ERR-001"));
        mockMvc.perform(put("/api/travel/trips/" + tripId + "/budget")
                        .header(HttpHeaders.AUTHORIZATION, otherAuthHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 800000}"))
                .andExpect(status().isNotFound());
    }

    // ---------------- helpers ----------------

    /** 오늘(1/15)이 2일차인 여행. */
    private long ongoingTrip() throws Exception {
        return createTrip("2026-01-14", "2026-01-16");
    }

    /** 이미 끝난 여행 — 「하루 평균」이 채워지는 상태. */
    private long completedTrip() throws Exception {
        return createTrip("2026-01-05", "2026-01-07");
    }

    private long createTrip(String start, String end) throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "일본", "startDate": "%s", "endDate": "%s", %s}
                                """.formatted(start, end,
                                TravelCityFixture.singleLeg(osaka, 3))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private void expense(long tripId, long amount, String title, String date) throws Exception {
        LedgerFixture.createTransaction(mockMvc, authHeader, """
                {"type": "EXPENSE", "amount": %d, "occurredOn": "%s",
                 "assetId": %d, "title": "%s", "tripId": %d}
                """.formatted(amount, date, checking, title, tripId));
    }

    private ResultActions expenses(long tripId) throws Exception {
        return mockMvc.perform(get("/api/travel/trips/" + tripId + "/expenses")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }

    private ResultActions putBudget(long tripId, String amount) throws Exception {
        return mockMvc.perform(put("/api/travel/trips/" + tripId + "/budget")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": %s}".formatted(amount)))
                .andExpect(status().isOk());
    }

    private long dayIdOf(long tripId, int index) throws Exception {
        String body = mockMvc.perform(get("/api/travel/trips/" + tripId + "/days")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data[%d].dayId".formatted(index))).longValue();
    }

    private void changeBaseCity(long dayId, long cityPlaceId) throws Exception {
        mockMvc.perform(put("/api/travel/days/" + dayId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseCityPlaceId\": %d}".formatted(cityPlaceId)))
                .andExpect(status().isOk());
    }
}
