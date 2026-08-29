package ds.project.orino.planner.ledger;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.planner.ledger.recurring.RecurringPostingScheduler;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.TestClocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정기 항목(#1263).
 *
 * <p>여기서 확인하는 것은 「자동으로 적히나」가 아니라 <b>두 번 적히지 않나</b>, <b>안 적어야
 * 할 것을 안 적나</b>다. 원장 중복은 잔액·통계·청구서·예산을 한꺼번에 틀어놓고 월말 대사에서야
 * 드러난다 — 그때는 어느 게 중복인지 가려낼 수 없다.
 *
 * <p>시계를 못박는다(2026-01-15, 목요일). 「밀린 기간 따라잡기」는 <b>다른 날짜로 스케줄러를
 * 부르는 것</b>으로 재현한다 — 실시각에 기대면 달이 바뀔 때마다 다른 결과가 난다.
 */
class LedgerRecurringTest extends ApiTestSupport {

    /** 시각을 못박는다. 설정을 나누지 않으므로 컨텍스트가 갈리지 않는다. */
    @Override
    protected Instant fixedNow() {
        return TestClocks.FIXED;
    }

    /** 고정 시계의 오늘. 목요일이다. */
    private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private RecurringPostingScheduler scheduler;

    private String authHeader;
    private long checking;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        checking = LedgerFixture.createAsset(mockMvc, authHeader, "급여통장", "CHECKING");
    }

    @Nested
    @DisplayName("중복은 DB가 막는다")
    class NeverTwice {

        /**
         * <b>이 이슈에서 가장 중요한 테스트다.</b> 스케줄러를 연달아 두 번 돌려도 행은 하나다.
         *
         * <p>{@code UNIQUE(recurring_id, occurrence_date)}가 두 번째 INSERT를 거부하고,
         * 스케줄러는 그 예외를 잡아 넘어간다 — 예외 처리가 아니라 설계다(D-2).
         */
        @Test
        @DisplayName("스케줄러를 두 번 연속 돌려도 회차는 한 번만 적힌다")
        void runningTwicePostsOnce() throws Exception {
            createRecurring("넷플릭스", 17000, "MONTHLY_DAY", 20, TODAY);

            scheduler.postDueOn(LocalDate.of(2026, 1, 20));
            scheduler.postDueOn(LocalDate.of(2026, 1, 20));

            assertTransactionCount("2026-01-01", "2026-01-31", 1);
        }

        @Test
        @DisplayName("스무 번을 돌려도 마찬가지다")
        void manyRunsPostOnce() throws Exception {
            createRecurring("넷플릭스", 17000, "MONTHLY_DAY", 20, TODAY);

            for (int i = 0; i < 20; i++) {
                scheduler.postDueOn(LocalDate.of(2026, 1, 20));
            }

            assertTransactionCount("2026-01-01", "2026-01-31", 1);
        }
    }

    @Nested
    @DisplayName("언제부터 적기 시작하나")
    class PostingFloor {

        /**
         * 과거 시작일로 새 항목을 만들었다고 지난 여섯 달치가 쏟아지면 안 된다. 시작일은
         * 「언제부터 쓰던 구독인가」이고, 적기 시작하는 날은 <b>등록한 날</b>이다.
         */
        @Test
        @DisplayName("최초 등록 시 과거 회차를 소급 생성하지 않는다")
        void neverBackfillsOnCreate() throws Exception {
            // 2025년 7월부터 쓰던 구독을 오늘 등록한다. 매월 10일이니 이미 여섯 번 지났다.
            createRecurring("멜론", 10900, "MONTHLY_DAY", 10, LocalDate.of(2025, 7, 10));

            scheduler.postDueOn(TODAY);

            assertTransactionCount("2025-01-01", "2026-01-31", 0);
        }

        /** 반대로, 등록한 뒤 밀린 기간은 전부 따라잡는다 — 서버가 며칠 꺼져 있었어도. */
        @Test
        @DisplayName("등록 뒤 밀린 회차는 전부 따라잡는다")
        void catchesUpAfterDowntime() throws Exception {
            createRecurring("넷플릭스", 17000, "MONTHLY_DAY", 20, TODAY);

            // 넉 달 만에 처음 도는 스케줄러.
            scheduler.postDueOn(LocalDate.of(2026, 4, 25));

            assertTransactionCount("2026-01-01", "2026-04-30", 4);
        }

        @Test
        @DisplayName("아직 오지 않은 회차는 적지 않는다")
        void doesNotPostFuture() throws Exception {
            createRecurring("넷플릭스", 17000, "MONTHLY_DAY", 20, TODAY);

            scheduler.postDueOn(LocalDate.of(2026, 1, 19));

            assertTransactionCount("2026-01-01", "2026-01-31", 0);
        }
    }

    @Nested
    @DisplayName("규칙을 고쳐도 과거는 그대로다")
    class ForwardOnly {

        /**
         * 지난달에 12,000원 낸 사실은 그대로 남아야 한다. "이 건만 / 이후 모두 / 전체"를
         * 묻지 않는 것은 <b>과거를 건드리지 않기 때문에</b> 물을 필요가 없어서다(§6.5).
         */
        @Test
        @DisplayName("금액을 올려도 이미 적힌 회차는 옛 금액 그대로다")
        void pastPostingsUnchanged() throws Exception {
            long id = createRecurring("넷플릭스", 12000, "MONTHLY_DAY", 20, TODAY);
            scheduler.postDueOn(LocalDate.of(2026, 1, 20));

            mockMvc.perform(patch("/api/ledger/recurring/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"amount": 17000}
                                    """))
                    .andExpect(status().isOk());
            scheduler.postDueOn(LocalDate.of(2026, 2, 20));

            // 1월은 옛 금액, 2월부터 새 금액.
            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("from", "2026-01-01").param("to", "2026-01-31"))
                    .andExpect(jsonPath("$.data.monthTotals.expense").value(12000));
            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("from", "2026-02-01").param("to", "2026-02-28"))
                    .andExpect(jsonPath("$.data.monthTotals.expense").value(17000));
        }

        @Test
        @DisplayName("금액 변경은 이력으로 남는다")
        void amountHistory() throws Exception {
            long id = createRecurring("넷플릭스", 12000, "MONTHLY_DAY", 20, TODAY);
            mockMvc.perform(patch("/api/ledger/recurring/" + id)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"amount": 17000}
                            """));

            mockMvc.perform(get("/api/ledger/recurring/" + id + "/history")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.amounts", hasSize(2)))
                    .andExpect(jsonPath("$.data.amounts[1].amount").value(17000))
                    .andExpect(jsonPath("$.data.amounts[1].changeFromAmount").value(12000));
        }

        /** 규칙 수정은 <b>앞으로의 예정</b>을 즉시 바꾼다. 대량 UPDATE 없이 저절로 그렇게 된다. */
        @Test
        @DisplayName("주기를 고치면 다음 결제일이 즉시 바뀐다")
        void ruleChangeMovesNextDate() throws Exception {
            long id = createRecurring("넷플릭스", 17000, "MONTHLY_DAY", 20, TODAY);
            mockMvc.perform(patch("/api/ledger/recurring/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"freqType": "MONTHLY_DAY", "freqDay": 5}
                                    """))
                    .andExpect(jsonPath("$.data.nextDate").value("2026-02-05"));
        }
    }

    @Nested
    @DisplayName("미납과 건너뛰기는 다른 것이다")
    class UnpaidVersusSkip {

        /**
         * 미납은 삭제가 아니다. 장부에서는 빠지되 <b>예정에 남고 경고로 계속 보인다</b> —
         * 안 낸 돈은 여전히 내야 할 돈이다(§6.4).
         */
        @Test
        @DisplayName("미납 처리하면 장부에서 빠지고 미납 목록에 남는다")
        void unpaidLeavesLedgerButStaysVisible() throws Exception {
            long id = createRecurring("보험료", 42000, "MONTHLY_DAY", 20, TODAY);
            scheduler.postDueOn(LocalDate.of(2026, 1, 20));

            markOccurrence(id, "2026-01-20", "UNPAID", null, null, "잔고 부족");

            assertTransactionCount("2026-01-01", "2026-01-31", 0);
            mockMvc.perform(get("/api/ledger/recurring")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.overdue", hasSize(1)))
                    .andExpect(jsonPath("$.data.overdue[0].amount").value(42000))
                    .andExpect(jsonPath("$.data.overdue[0].note").value("잔고 부족"));
        }

        /** 사람이 「안 빠졌다」고 표시한 것을 배치가 매시간 뒤집으면 안 된다. */
        @Test
        @DisplayName("미납으로 표시한 회차를 스케줄러가 다시 적지 않는다")
        void schedulerDoesNotRepostUnpaid() throws Exception {
            long id = createRecurring("보험료", 42000, "MONTHLY_DAY", 20, TODAY);
            scheduler.postDueOn(LocalDate.of(2026, 1, 20));
            markOccurrence(id, "2026-01-20", "UNPAID", null, null, null);

            scheduler.postDueOn(LocalDate.of(2026, 1, 25));

            assertTransactionCount("2026-01-01", "2026-01-31", 0);
        }

        @Test
        @DisplayName("건너뛴 회차는 적히지도, 미납으로 남지도 않는다")
        void skipDisappears() throws Exception {
            long id = createRecurring("보험료", 42000, "MONTHLY_DAY", 20, TODAY);
            markOccurrence(id, "2026-01-20", "SKIP", null, null, null);

            scheduler.postDueOn(LocalDate.of(2026, 1, 25));

            assertTransactionCount("2026-01-01", "2026-01-31", 0);
            mockMvc.perform(get("/api/ledger/recurring")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.overdue", hasSize(0)));
        }

        /**
         * 며칠 늦게 빠진 것을 <b>새 거래로</b> 적으면 「이번 달에 두 번 냈다」가 된다.
         * 그래서 확정은 그 회차를 되살려 날짜만 옮긴다.
         */
        @Test
        @DisplayName("미납을 확정하면 실제 출금일로 옮겨지고 행이 늘지 않는다")
        void confirmMovesInsteadOfAdding() throws Exception {
            long id = createRecurring("보험료", 42000, "MONTHLY_DAY", 20, TODAY);
            scheduler.postDueOn(LocalDate.of(2026, 1, 20));
            markOccurrence(id, "2026-01-20", "UNPAID", null, null, null);

            mockMvc.perform(post("/api/ledger/upcoming/occurrence/confirm")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"recurringId": %d, "occurrenceDate": "2026-01-20",
                                     "actualDate": "2026-01-23"}
                                    """.formatted(id)))
                    .andExpect(status().isOk());

            assertTransactionCount("2026-01-01", "2026-01-31", 1);
            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("from", "2026-01-01").param("to", "2026-01-31"))
                    .andExpect(jsonPath("$.data.groups[0].date").value("2026-01-23"));
            mockMvc.perform(get("/api/ledger/recurring")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.overdue", hasSize(0)));
        }

        @Test
        @DisplayName("미납이 아닌 회차는 확정할 수 없다")
        void confirmRequiresUnpaid() throws Exception {
            long id = createRecurring("보험료", 42000, "MONTHLY_DAY", 20, TODAY);
            scheduler.postDueOn(LocalDate.of(2026, 1, 20));

            mockMvc.perform(post("/api/ledger/upcoming/occurrence/confirm")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"recurringId": %d, "occurrenceDate": "2026-01-20",
                                     "actualDate": "2026-01-23"}
                                    """.formatted(id)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-012"));
        }

        @Test
        @DisplayName("이번 회차만 금액을 고치면 적힌 거래도 함께 고쳐진다")
        void amountOverrideFixesPosting() throws Exception {
            long id = createRecurring("전기요금", 30000, "MONTHLY_DAY", 20, TODAY);
            scheduler.postDueOn(LocalDate.of(2026, 1, 20));

            markOccurrence(id, "2026-01-20", "AMOUNT", 41200L, null, null);

            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("from", "2026-01-01").param("to", "2026-01-31"))
                    .andExpect(jsonPath("$.data.monthTotals.expense").value(41200));
        }
    }

    @Nested
    @DisplayName("해지")
    class Ending {

        /**
         * 소급 해지는 이미 원장에 들어간 것을 지우는 유일한 경로다. 묻지 않고 지우면 3월에
         * 해지한 것을 8월에 등록하면서 다섯 달치가 소리 없이 사라진다.
         */
        @Test
        @DisplayName("되돌리기를 켜면 해지일 이후 자동 기록이 일괄로 빠진다")
        void revertsPostedAfterEndedOn() throws Exception {
            long id = createRecurring("넷플릭스", 17000, "MONTHLY_DAY", 20, TODAY);
            scheduler.postDueOn(LocalDate.of(2026, 4, 25));
            assertTransactionCount("2026-01-01", "2026-04-30", 4);

            mockMvc.perform(post("/api/ledger/recurring/" + id + "/end")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"endedOn": "2026-03-01", "revertPostedAfter": true}
                                    """))
                    .andExpect(jsonPath("$.data.reverted").value(2));

            // 1·2월은 남고 3·4월만 빠진다.
            assertTransactionCount("2026-01-01", "2026-04-30", 2);
        }

        @Test
        @DisplayName("되돌리기를 끄면 이미 적힌 것은 그대로 남는다")
        void keepsPostedWhenNotReverting() throws Exception {
            long id = createRecurring("넷플릭스", 17000, "MONTHLY_DAY", 20, TODAY);
            scheduler.postDueOn(LocalDate.of(2026, 4, 25));

            mockMvc.perform(post("/api/ledger/recurring/" + id + "/end")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"endedOn": "2026-03-01", "revertPostedAfter": false}
                                    """))
                    .andExpect(jsonPath("$.data.reverted").value(0));

            assertTransactionCount("2026-01-01", "2026-04-30", 4);
        }

        /** 되돌린 회차는 이력에 남는다 — 몇 달째 되돌리고 있는지 보여야 규칙을 정리한다. */
        @Test
        @DisplayName("되돌린 회차가 미발생 이력에 남는다")
        void revertedStaysInHistory() throws Exception {
            long id = createRecurring("넷플릭스", 17000, "MONTHLY_DAY", 20, TODAY);
            scheduler.postDueOn(LocalDate.of(2026, 4, 25));
            mockMvc.perform(post("/api/ledger/recurring/" + id + "/end")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"endedOn": "2026-03-01", "revertPostedAfter": true}
                            """));

            mockMvc.perform(get("/api/ledger/recurring/" + id + "/history")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.missed", hasSize(2)))
                    .andExpect(jsonPath("$.data.missed[0].action").value("REVERTED"));
        }

        /** 해지해도 목록에서 사라지지 않는다 — 연간 고정비 회고에 필요하다(§6.6). */
        @Test
        @DisplayName("해지한 항목은 「종료됨」으로 목록에 남는다")
        void endedStaysInList() throws Exception {
            long id = createRecurring("넷플릭스", 17000, "MONTHLY_DAY", 20, TODAY);
            mockMvc.perform(post("/api/ledger/recurring/" + id + "/end")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"endedOn": "2026-01-15", "revertPostedAfter": false}
                            """));

            mockMvc.perform(get("/api/ledger/recurring")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.items[0].status").value("ENDED"))
                    .andExpect(jsonPath("$.data.stats.activeCount").value(0));
        }

        @Test
        @DisplayName("정지 구간의 회차는 적히지 않고 그 뒤는 다시 적힌다")
        void pausedRangeSkipped() throws Exception {
            long id = createRecurring("넷플릭스", 17000, "MONTHLY_DAY", 20, TODAY);
            mockMvc.perform(post("/api/ledger/recurring/" + id + "/pause")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"from": "2026-02-01", "to": "2026-03-31"}
                                    """))
                    .andExpect(status().isOk());

            scheduler.postDueOn(LocalDate.of(2026, 4, 25));

            assertTransactionCount("2026-01-01", "2026-04-30", 2);
        }
    }

    @Nested
    @DisplayName("적어선 안 되는 것")
    class Excluded {

        /**
         * 자동 기록의 <b>유일한 제외 대상</b>이다(§7.2). 잔고 부족·리볼빙·선결제 때문에
         * 실제 출금액을 앱이 알 수 없어서, 만드는 길 자체를 막는다.
         */
        @Test
        @DisplayName("카드 대금은 정기 항목으로 만들 수 없다")
        void cardPaymentRejected() throws Exception {
            long card = LedgerFixture.createAsset(mockMvc, authHeader, "신한카드", "CREDIT_CARD");

            mockMvc.perform(post("/api/ledger/recurring")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "카드값", "kind": "TRANSFER", "txType": "TRANSFER",
                                     "amount": 500000, "assetId": %d, "counterAssetId": %d,
                                     "freqType": "MONTHLY_DAY", "freqDay": 14,
                                     "startDate": "2026-01-15"}
                                    """.formatted(checking, card)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-013"));
        }

        @Test
        @DisplayName("주기에 필요한 값이 없으면 저장 시점에 거부한다")
        void incompleteRuleRejected() throws Exception {
            mockMvc.perform(post("/api/ledger/recurring")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "적금", "kind": "FIXED_COST", "txType": "EXPENSE",
                                     "amount": 100000, "assetId": %d,
                                     "freqType": "EVERY_N_MONTHS", "freqDay": 10,
                                     "startDate": "2026-01-15"}
                                    """.formatted(checking)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-012"));
        }

        /** 규칙에 없는 날짜로 회차를 만들면 예정과 미납 경고에 유령이 남는다. */
        @Test
        @DisplayName("규칙이 내지 않는 날짜는 손댈 수 없다")
        void phantomOccurrenceRejected() throws Exception {
            long id = createRecurring("넷플릭스", 17000, "MONTHLY_DAY", 20, TODAY);

            mockMvc.perform(patch("/api/ledger/upcoming/occurrence")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"recurringId": %d, "occurrenceDate": "2026-01-21",
                                     "action": "SKIP"}
                                    """.formatted(id)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LDG-ERR-012"));
        }

        /**
         * 막으면 「돈이 없어서 안 적힌 회차」가 생긴다. 그건 실제로 빠져나간 돈을 장부에서
         * 지우는 것과 같다 — 경고는 하되 기록은 남긴다(§6.3).
         */
        @Test
        @DisplayName("잔액이 음수가 되어도 기록 자체를 막지 않는다")
        void negativeBalanceDoesNotBlock() throws Exception {
            createRecurring("월세", 800000, "MONTHLY_DAY", 20, TODAY);

            scheduler.postDueOn(LocalDate.of(2026, 1, 20));

            assertTransactionCount("2026-01-01", "2026-01-31", 1);
            mockMvc.perform(get("/api/ledger/assets")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.totalAssets").value(-800000));
        }
    }

    @Nested
    @DisplayName("영업일 보정")
    class BusinessDay {

        /**
         * 보정된 날짜는 <b>회차의 키가 아니다.</b> 키는 규칙이 계산한 원래 예정일(1/18)이고
         * 보정은 실제로 빠지는 날(1/16)만 정한다 — 그래야 공휴일 자료가 늦게 갱신돼도
         * 같은 회차가 두 번 적히지 않는다.
         */
        @Test
        @DisplayName("주말에 걸린 회차는 앞 영업일에 적히고, 다시 돌려도 늘지 않는다")
        void weekendPulledForward() throws Exception {
            // 2026-01-18은 일요일이다.
            mockMvc.perform(post("/api/ledger/recurring")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "관리비", "kind": "FIXED_COST", "txType": "EXPENSE",
                                     "amount": 120000, "assetId": %d,
                                     "freqType": "MONTHLY_DAY", "freqDay": 18,
                                     "businessDayPolicy": "PREV", "startDate": "2026-01-15"}
                                    """.formatted(checking)))
                    .andExpect(status().isOk());

            scheduler.postDueOn(LocalDate.of(2026, 1, 20));
            scheduler.postDueOn(LocalDate.of(2026, 1, 20));

            assertTransactionCount("2026-01-01", "2026-01-31", 1);
            mockMvc.perform(get("/api/ledger/transactions")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .param("from", "2026-01-01").param("to", "2026-01-31"))
                    .andExpect(jsonPath("$.data.groups[0].date").value("2026-01-16"));
        }
    }

    @Nested
    @DisplayName("목록은 점검 도구다")
    class Signals {

        @Test
        @DisplayName("연간 구독은 월 환산으로 고정비에 얹힌다")
        void yearlyDividedIntoMonthly() throws Exception {
            mockMvc.perform(post("/api/ledger/recurring")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name": "도메인", "kind": "SUBSCRIPTION", "txType": "EXPENSE",
                             "amount": 120000, "assetId": %d,
                             "freqType": "YEARLY", "freqMonth": 3, "freqDay": 1,
                             "startDate": "2026-03-01"}
                            """.formatted(checking)));

            mockMvc.perform(get("/api/ledger/recurring")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.stats.monthlyFixedTotal").value(10000))
                    .andExpect(jsonPath("$.data.stats.yearlyTotal").value(120000));
        }

        @Test
        @DisplayName("최근 인상과 무기한 항목이 신호로 잡힌다")
        void priceIncreaseAndNoEndDate() throws Exception {
            long id = createRecurring("넷플릭스", 12000, "MONTHLY_DAY", 20, TODAY);
            mockMvc.perform(patch("/api/ledger/recurring/" + id)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"amount": 17000}
                            """));

            mockMvc.perform(get("/api/ledger/recurring")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.signals.priceIncreased", hasSize(1)))
                    .andExpect(jsonPath("$.data.signals.priceIncreased[0].from").value(12000))
                    .andExpect(jsonPath("$.data.signals.priceIncreased[0].to").value(17000))
                    .andExpect(jsonPath("$.data.signals.noEndDate", hasSize(1)));
        }

        /** 아직 한 번도 안 나갔는데 곧 처음 나간다 = 무료 체험이 끝나간다. */
        @Test
        @DisplayName("첫 결제가 임박한 항목이 무료 체험 종료로 잡힌다")
        void trialEnding() throws Exception {
            createRecurring("왓챠", 7900, "MONTHLY_DAY", 22, LocalDate.of(2026, 1, 22));

            mockMvc.perform(get("/api/ledger/recurring")
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(jsonPath("$.data.signals.trialEnding", hasSize(1)))
                    .andExpect(jsonPath("$.data.signals.trialEnding[0].endsOn")
                            .value("2026-01-22"));
        }
    }

    private long createRecurring(String name, long amount, String freqType,
                                 int freqDay, LocalDate startDate) throws Exception {
        String body = mockMvc.perform(post("/api/ledger/recurring")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "kind": "SUBSCRIPTION", "txType": "EXPENSE",
                                 "amount": %d, "assetId": %d,
                                 "freqType": "%s", "freqDay": %d, "startDate": "%s"}
                                """.formatted(name, amount, checking, freqType, freqDay,
                                startDate)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private void markOccurrence(long recurringId, String occurrenceDate, String action,
                                Long amount, String movedTo, String note) throws Exception {
        mockMvc.perform(patch("/api/ledger/upcoming/occurrence")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recurringId": %d, "occurrenceDate": "%s", "action": "%s",
                                 "amount": %s, "movedTo": %s, "note": %s}
                                """.formatted(recurringId, occurrenceDate, action,
                                amount, quote(movedTo), quote(note))))
                .andExpect(status().isOk());
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    /** 원장에 몇 줄이 있는가. 중복 여부는 결국 이 숫자가 답한다. */
    private void assertTransactionCount(String from, String to, int expected) throws Exception {
        String body = mockMvc.perform(get("/api/ledger/transactions")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("from", from).param("to", to))
                .andReturn().getResponse().getContentAsString();
        List<Object> items = JsonPath.read(body, "$.data.groups[*].items[*]");
        org.assertj.core.api.Assertions.assertThat(items).hasSize(expected);
    }
}
