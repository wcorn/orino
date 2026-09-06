package ds.project.orino.planner.travel.prep;

import com.jayway.jsonpath.JsonPath;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.travel.repository.TripPrepItemRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 준비 CRUD(API §10).
 *
 * <p>이 테스트가 지키는 것은 <b>기한이 날짜가 아니라 D−N이라는 사실</b>이다. 출발일을 하루
 * 당기면 기한도 따라 움직여야 하는데, 어디선가 날짜로 굳어 버리면 화면은 멀쩡해 보이고
 * 하루 늦은 기한만 남는다 — 그때는 알아챌 방법이 없다.
 *
 * <p>시각을 못박는 이유도 같다. 기한 지남은 <b>첫날 기준 도시의 오늘</b>로 판정하므로
 * 실시각으로 돌리면 어느 날 갑자기 색이 바뀐다.
 */
// 스텁 조합을 새로 만들지 않는다 — 조합이 늘 때마다 Spring 컨텍스트가 하나씩 더 뜬다.
@FixedClock("2026-10-05T03:00:00Z")
class PrepControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TripPrepItemRepository prepRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private String authHeader;
    private String otherAuthHeader;
    private long tripId;
    private long osaka;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        memberRepository.save(MemberFixture.create());
        memberRepository.save(MemberFixture.create("other", "password"));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
        otherAuthHeader = "Bearer "
                + AuthFixture.loginAndGetAccessToken(mockMvc, "other", "password");

        osaka = TravelCityFixture.createCity(mockMvc, authHeader, "오사카", "Asia/Tokyo", "JPY");
        tripId = createTrip("2026-10-24", "2026-10-27", 4);
    }

    @Nested
    @DisplayName("조회")
    class Read {

        @Test
        @DisplayName("항목이 하나도 없어도 분류 4개가 전부 내려온다")
        void alwaysReturnsFourGroups() throws Exception {
            getPrep()
                    .andExpect(jsonPath("$.data.groups", hasSize(4)))
                    .andExpect(jsonPath("$.data.groups[0].category").value("DOCUMENT"))
                    .andExpect(jsonPath("$.data.groups[1].category").value("BOOKING"))
                    .andExpect(jsonPath("$.data.groups[2].category").value("BAG"))
                    .andExpect(jsonPath("$.data.groups[3].category").value("TODO"))
                    .andExpect(jsonPath("$.data.groups[0].sections", hasSize(0)))
                    .andExpect(jsonPath("$.data.total").value(0))
                    .andExpect(jsonPath("$.data.done").value(0))
                    .andExpect(jsonPath("$.data.overdueCount").value(0));
        }

        @Test
        @DisplayName("D-day는 첫날 기준 도시의 오늘로 센다 — 서버 로컬 날짜가 아니다")
        void countsDdayInFirstCity() throws Exception {
            // 못박은 시각은 UTC 10/5 03:00. 오사카(UTC+9)에서는 10/5 정오라 D-19다.
            getPrep()
                    .andExpect(jsonPath("$.data.startDate").value("2026-10-24"))
                    .andExpect(jsonPath("$.data.dday").value(19));
        }

        @Test
        @DisplayName("진행률과 기한 지남 개수는 여행 전체를 기준으로 하나씩만 센다")
        void countsWholeTrip() throws Exception {
            create("""
                    {"category": "BOOKING", "title": "항공권 발권"}""");
            long overdue = createAndGetId("""
                    {"category": "BOOKING", "title": "여권 갱신", "dueDaysBefore": 20}""");
            long done = createAndGetId("""
                    {"category": "BAG", "title": "멀티어댑터"}""");
            check(done, true);

            getPrep()
                    .andExpect(jsonPath("$.data.total").value(3))
                    .andExpect(jsonPath("$.data.done").value(1))
                    // 기한 10/4가 오늘(10/5)보다 앞이다.
                    .andExpect(jsonPath("$.data.overdueCount").value(1))
                    .andExpect(jsonPath("$.data.groups[1].total").value(2))
                    .andExpect(jsonPath("$.data.groups[1].done").value(0))
                    .andExpect(jsonPath("$.data.groups[2].done").value(1));

            assertThat(prepRepository.findById(overdue).orElseThrow().getDueDaysBefore())
                    .isEqualTo(20);
        }

        @Test
        @DisplayName("체크한 항목은 기한이 지나도 세지 않는다")
        void doneIsNotOverdue() throws Exception {
            long id = createAndGetId("""
                    {"category": "BOOKING", "title": "여권 갱신", "dueDaysBefore": 20}""");

            check(id, true)
                    .andExpect(jsonPath("$.data.summary.overdueCount").value(0));
        }

        @Test
        @DisplayName("남의 여행은 404 — 있다는 사실조차 알려주지 않는다")
        void hidesOthersTrip() throws Exception {
            mockMvc.perform(get("/api/travel/trips/" + tripId + "/prep")
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-001"));
        }
    }

    @Nested
    @DisplayName("기한은 D−N으로만 산다")
    class DueDaysBefore {

        @Test
        @DisplayName("출발일을 하루 당기면 기한 날짜도 하루 당겨진다")
        void followsStartDate() throws Exception {
            create("""
                    {"category": "BOOKING", "title": "숙소 잔금 결제", "dueDaysBefore": 14}""");

            getPrep()
                    .andExpect(jsonPath("$.data.groups[1].sections[0].items[0].dueDate")
                            .value("2026-10-10"))
                    .andExpect(jsonPath("$.data.groups[1].sections[0].items[0].dueDaysBefore").value(14));

            // 날짜로 저장했다면 10/10에 그대로 남아 조용히 하루 늦은 기한이 됐을 자리다.
            updateTripPeriod("2026-10-23", "2026-10-26");

            getPrep()
                    .andExpect(jsonPath("$.data.startDate").value("2026-10-23"))
                    .andExpect(jsonPath("$.data.groups[1].sections[0].items[0].dueDate")
                            .value("2026-10-09"))
                    .andExpect(jsonPath("$.data.groups[1].sections[0].items[0].dueDaysBefore").value(14));
        }

        @Test
        @DisplayName("출발일이 밀리면 지났던 기한이 다시 살아난다")
        void revivesOverdueWhenStartMovesLater() throws Exception {
            create("""
                    {"category": "BOOKING", "title": "여권 갱신", "dueDaysBefore": 20}""");

            getPrep().andExpect(jsonPath("$.data.overdueCount").value(1));

            updateTripPeriod("2026-10-28", "2026-10-31");

            // 기한이 10/4 → 10/8로 밀려 오늘(10/5)보다 뒤가 된다.
            getPrep()
                    .andExpect(jsonPath("$.data.overdueCount").value(0))
                    .andExpect(jsonPath("$.data.groups[1].sections[0].items[0].overdue").value(false));
        }

        @Test
        @DisplayName("음수 기한은 400 — 「출발 3일 후」는 준비가 아니다")
        void rejectsNegativeDue() throws Exception {
            mockMvc.perform(post("/api/travel/trips/" + tripId + "/prep/items")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "공항 픽업", "dueDaysBefore": -3}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-023"));
        }
    }

    @Nested
    @DisplayName("추가")
    class Create {

        @Test
        @DisplayName("제목만 보내면 할 일로 들어간다 — 애매하면 할 일")
        void defaultsToTodo() throws Exception {
            create("""
                    {"title": "환전"}""")
                    .andExpect(jsonPath("$.data.category").value("TODO"))
                    .andExpect(jsonPath("$.data.item.title").value("환전"))
                    .andExpect(jsonPath("$.data.item.done").value(false))
                    .andExpect(jsonPath("$.data.item.displayOrder").value(0))
                    .andExpect(jsonPath("$.data.summary.total").value(1));
        }

        @Test
        @DisplayName("순서는 서버가 그 분류의 맨 뒤로 정한다")
        void appendsToEndOfCategory() throws Exception {
            create("""
                    {"category": "BAG", "title": "멀티어댑터"}""")
                    .andExpect(jsonPath("$.data.item.displayOrder").value(0));
            create("""
                    {"category": "BAG", "title": "양말"}""")
                    .andExpect(jsonPath("$.data.item.displayOrder").value(1));
            // 분류마다 따로 센다 — 짐이 둘 있어도 서류의 첫 항목은 0이다.
            create("""
                    {"category": "DOCUMENT", "title": "여권"}""")
                    .andExpect(jsonPath("$.data.item.displayOrder").value(0));
        }

        @Test
        @DisplayName("짐이 아닌데 수량을 보내면 400이 아니라 NULL로 떨어진다")
        void dropsQuantityOutsideBag() throws Exception {
            create("""
                    {"category": "TODO", "title": "환전", "quantity": 4}""")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.item.quantity").value(nullValue()));
        }

        @Test
        @DisplayName("짐이면 수량이 남는다")
        void keepsQuantityForBag() throws Exception {
            create("""
                    {"category": "BAG", "title": "양말", "quantity": 4}""")
                    .andExpect(jsonPath("$.data.item.quantity").value(4));
        }

        @Test
        @DisplayName("제목이 비면 400")
        void rejectsBlankTitle() throws Exception {
            mockMvc.perform(post("/api/travel/trips/" + tripId + "/prep/items")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "  "}"""))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("수정")
    class Patch {

        @Test
        @DisplayName("체크 한 번에 갱신된 집계가 함께 온다")
        void returnsSummaryWithToggle() throws Exception {
            long id = createAndGetId("""
                    {"category": "BAG", "title": "멀티어댑터"}""");
            create("""
                    {"category": "BAG", "title": "양말"}""");

            check(id, true)
                    .andExpect(jsonPath("$.data.item.done").value(true))
                    .andExpect(jsonPath("$.data.summary.total").value(2))
                    .andExpect(jsonPath("$.data.summary.done").value(1));

            check(id, false)
                    .andExpect(jsonPath("$.data.summary.done").value(0));
        }

        @Test
        @DisplayName("보낸 것만 바뀐다 — 체크 토글이 제목을 지우지 않는다")
        void patchesOnlyWhatWasSent() throws Exception {
            long id = createAndGetId("""
                    {"category": "BOOKING", "title": "숙소 잔금 결제",
                     "dueDaysBefore": 14, "url": "https://example.com", "memo": "카드로"}""");

            check(id, true);

            getPrep()
                    .andExpect(jsonPath("$.data.groups[1].sections[0].items[0].title")
                            .value("숙소 잔금 결제"))
                    .andExpect(jsonPath("$.data.groups[1].sections[0].items[0].dueDaysBefore").value(14))
                    .andExpect(jsonPath("$.data.groups[1].sections[0].items[0].url")
                            .value("https://example.com"))
                    .andExpect(jsonPath("$.data.groups[1].sections[0].items[0].memo").value("카드로"));
        }

        @Test
        @DisplayName("지우려면 지운다고 말해야 한다 — clear에 적힌 칸만 비워진다")
        void clearsOnlyNamedFields() throws Exception {
            long id = createAndGetId("""
                    {"category": "BOOKING", "title": "숙소 잔금 결제",
                     "dueDaysBefore": 14, "memo": "카드로"}""");

            patchItem(id, """
                    {"clear": ["DUE_DAYS_BEFORE"]}""")
                    .andExpect(jsonPath("$.data.item.dueDaysBefore").value(nullValue()))
                    .andExpect(jsonPath("$.data.item.dueDate").value(nullValue()))
                    .andExpect(jsonPath("$.data.item.memo").value("카드로"));
        }

        @Test
        @DisplayName("분류를 옮기면 새 분류의 맨 뒤로 간다")
        void movesToEndOfNewCategory() throws Exception {
            long moving = createAndGetId("""
                    {"category": "TODO", "title": "멀티어댑터"}""");
            create("""
                    {"category": "BAG", "title": "양말"}""");
            create("""
                    {"category": "BAG", "title": "충전기"}""");

            patchItem(moving, """
                    {"category": "BAG"}""")
                    .andExpect(jsonPath("$.data.category").value("BAG"))
                    .andExpect(jsonPath("$.data.item.displayOrder").value(2));

            getPrep()
                    .andExpect(jsonPath("$.data.groups[2].sections[0].items[2].title").value("멀티어댑터"))
                    .andExpect(jsonPath("$.data.groups[3].sections", hasSize(0)));
        }

        @Test
        @DisplayName("짐으로 옮기면서 보낸 수량은 남는다 — 분류를 먼저 옮긴다")
        void keepsQuantityWhenMovingIntoBag() throws Exception {
            long id = createAndGetId("""
                    {"category": "TODO", "title": "양말"}""");

            patchItem(id, """
                    {"category": "BAG", "quantity": 4}""")
                    .andExpect(jsonPath("$.data.item.quantity").value(4));
        }

        @Test
        @DisplayName("짐에서 나가면 수량은 함께 사라진다")
        void dropsQuantityWhenLeavingBag() throws Exception {
            long id = createAndGetId("""
                    {"category": "BAG", "title": "양말", "quantity": 4}""");

            patchItem(id, """
                    {"category": "TODO"}""")
                    .andExpect(jsonPath("$.data.item.quantity").value(nullValue()));
        }

        @Test
        @DisplayName("남의 항목은 404")
        void hidesOthersItem() throws Exception {
            long id = createAndGetId("""
                    {"title": "환전"}""");

            mockMvc.perform(patch("/api/travel/prep/items/" + id)
                            .header(HttpHeaders.AUTHORIZATION, otherAuthHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"done": true}"""))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-022"));
        }
    }

    /**
     * 묶음(#1358). 분류를 다섯 번째로 늘리는 대신 그 안에 사용자가 이름 붙이는 겹을 하나 뒀다.
     *
     * <p>여기서 지키는 것은 <b>묶음의 자리를 저장하지 않는다</b>는 사실이다. 순서 컬럼을 따로
     * 두면 항목을 옮길 때마다 둘을 맞춰야 하고, 어긋난 것이 화면에는 안 보인다 — 그래서
     * 자리는 언제나 그 안의 최소 {@code displayOrder}에서 다시 나온다.
     */
    @Nested
    @DisplayName("묶음")
    class Section {

        @Test
        @DisplayName("아무도 묶지 않으면 이름 없는 묶음 하나로 내려간다")
        void wrapsUnlabeledInSingleSection() throws Exception {
            create("""
                    {"category": "BAG", "title": "멀티어댑터"}""");
            create("""
                    {"category": "BAG", "title": "양말"}""");

            getPrep()
                    .andExpect(jsonPath("$.data.groups[2].sections", hasSize(1)))
                    .andExpect(jsonPath("$.data.groups[2].sections[0].label")
                            .value(nullValue()))
                    .andExpect(jsonPath("$.data.groups[2].sections[0].items", hasSize(2)));
        }

        @Test
        @DisplayName("묶음 없음은 나중에 적혔어도 맨 앞이다")
        void unlabeledSectionComesFirst() throws Exception {
            create("""
                    {"category": "BAG", "title": "충전기", "sectionLabel": "캐리어"}""");
            create("""
                    {"category": "BAG", "title": "여권 지갑"}""");

            // 아무것도 안 나눈 목록이 가운데로 밀리면, 묶음을 만든 대가를 안 만든 항목이 친다.
            getPrep()
                    .andExpect(jsonPath("$.data.groups[2].sections", hasSize(2)))
                    .andExpect(jsonPath("$.data.groups[2].sections[0].label")
                            .value(nullValue()))
                    .andExpect(jsonPath("$.data.groups[2].sections[0].items[0].title")
                            .value("여권 지갑"))
                    .andExpect(jsonPath("$.data.groups[2].sections[1].label").value("캐리어"));
        }

        @Test
        @DisplayName("묶음의 자리는 그 안의 첫 항목이 정한다 — 순서를 따로 저장하지 않는다")
        void ordersSectionsByFirstItem() throws Exception {
            create("""
                    {"category": "BAG", "title": "충전기", "sectionLabel": "캐리어"}""");
            create("""
                    {"category": "BAG", "title": "칫솔", "sectionLabel": "세면백"}""");
            create("""
                    {"category": "BAG", "title": "옷", "sectionLabel": "캐리어"}""");

            getPrep()
                    .andExpect(jsonPath("$.data.groups[2].sections", hasSize(2)))
                    .andExpect(jsonPath("$.data.groups[2].sections[0].label").value("캐리어"))
                    .andExpect(jsonPath("$.data.groups[2].sections[0].total").value(2))
                    .andExpect(jsonPath("$.data.groups[2].sections[0].items[1].title")
                            .value("옷"))
                    .andExpect(jsonPath("$.data.groups[2].sections[1].label").value("세면백"));
        }

        @Test
        @DisplayName("묶음별 진행률은 따로 센다")
        void countsDonePerSection() throws Exception {
            long id = createAndGetId("""
                    {"category": "BAG", "title": "충전기", "sectionLabel": "캐리어"}""");
            create("""
                    {"category": "BAG", "title": "옷", "sectionLabel": "캐리어"}""");
            create("""
                    {"category": "BAG", "title": "칫솔", "sectionLabel": "세면백"}""");
            check(id, true);

            getPrep()
                    .andExpect(jsonPath("$.data.groups[2].done").value(1))
                    .andExpect(jsonPath("$.data.groups[2].sections[0].done").value(1))
                    .andExpect(jsonPath("$.data.groups[2].sections[0].total").value(2))
                    .andExpect(jsonPath("$.data.groups[2].sections[1].done").value(0));
        }

        @Test
        @DisplayName("묶음을 옮기면 그 묶음의 맨 뒤로 간다")
        void movesToEndOfSection() throws Exception {
            create("""
                    {"category": "BAG", "title": "충전기", "sectionLabel": "캐리어"}""");
            long moving = createAndGetId("""
                    {"category": "BAG", "title": "이어폰"}""");

            patchItem(moving, """
                    {"sectionLabel": "캐리어"}""")
                    .andExpect(jsonPath("$.data.item.sectionLabel").value("캐리어"))
                    .andExpect(jsonPath("$.data.item.displayOrder").value(2));

            // 옮긴 항목이 묶음 한가운데 나타나면, 옮긴 사람이 방금 누른 줄을 다시 찾아야 한다.
            getPrep()
                    .andExpect(jsonPath("$.data.groups[2].sections", hasSize(1)))
                    .andExpect(jsonPath("$.data.groups[2].sections[0].items[1].title")
                            .value("이어폰"));
        }

        @Test
        @DisplayName("같은 묶음을 다시 보내면 자리가 그대로다 — 앞뒤 공백은 같은 이름이다")
        void keepsOrderWhenSectionUnchanged() throws Exception {
            long id = createAndGetId("""
                    {"category": "BAG", "title": "충전기", "sectionLabel": "캐리어"}""");
            create("""
                    {"category": "BAG", "title": "옷", "sectionLabel": "캐리어"}""");

            // 손대지 않은 순서가 저장할 때마다 흔들리면, 목록이 자기 마음대로 움직이는 것으로
            // 보인다 — 저장 버튼은 제목만 고쳤을 때도 눌린다.
            patchItem(id, """
                    {"title": "충전기", "sectionLabel": "  캐리어 "}""")
                    .andExpect(jsonPath("$.data.item.displayOrder").value(0));

            getPrep()
                    .andExpect(jsonPath("$.data.groups[2].sections[0].items[0].title")
                            .value("충전기"));
        }

        @Test
        @DisplayName("묶음에서 빼려면 지운다고 말해야 한다")
        void clearsSectionOnlyWhenNamed() throws Exception {
            long id = createAndGetId("""
                    {"category": "BAG", "title": "충전기", "sectionLabel": "캐리어"}""");

            check(id, true)
                    .andExpect(jsonPath("$.data.item.sectionLabel").value("캐리어"));

            patchItem(id, """
                    {"clear": ["SECTION_LABEL"]}""")
                    .andExpect(jsonPath("$.data.item.sectionLabel").value(nullValue()));
        }

        @Test
        @DisplayName("분류를 옮겨도 묶음 이름은 따라간다 — 수량과 다르다")
        void keepsSectionAcrossCategories() throws Exception {
            long id = createAndGetId("""
                    {"category": "BAG", "title": "충전기", "sectionLabel": "캐리어",
                     "quantity": 2}""");

            // 수량은 짐에서만 뜻이 있다고 우리가 정의한 값이고, 묶음은 사용자가 적은 말이다.
            patchItem(id, """
                    {"category": "TODO"}""")
                    .andExpect(jsonPath("$.data.item.quantity").value(nullValue()))
                    .andExpect(jsonPath("$.data.item.sectionLabel").value("캐리어"));
        }

        @Test
        @DisplayName("분류와 묶음을 함께 옮겨도 자리는 새 분류의 맨 뒤 하나다")
        void movesOnceWhenCategoryAndSectionChangeTogether() throws Exception {
            create("""
                    {"category": "BAG", "title": "충전기", "sectionLabel": "캐리어"}""");
            create("""
                    {"category": "BAG", "title": "옷"}""");
            long moving = createAndGetId("""
                    {"category": "TODO", "title": "이어폰"}""");

            // 분류를 옮기면서 자리를 이미 맨 뒤로 받았다. 묶음 때문에 또 매기면 빈 자리가 쌓인다.
            patchItem(moving, """
                    {"category": "BAG", "sectionLabel": "캐리어"}""")
                    .andExpect(jsonPath("$.data.item.displayOrder").value(2));
        }

        @Test
        @DisplayName("공백만 적은 묶음은 묶음 없음이다")
        void treatsBlankLabelAsNoSection() throws Exception {
            create("""
                    {"category": "BAG", "title": "충전기", "sectionLabel": "   "}""")
                    .andExpect(jsonPath("$.data.item.sectionLabel").value(nullValue()));
        }

        @Test
        @DisplayName("30자를 넘는 묶음 이름은 400")
        void rejectsTooLongLabel() throws Exception {
            mockMvc.perform(post("/api/travel/trips/" + tripId + "/prep/items")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "충전기", "sectionLabel": "%s"}"""
                                    .formatted("가".repeat(31))))
                    .andExpect(status().isBadRequest());
        }
    }

    /**
     * 옛 번들이 읽는 평면 목록(#1361). <b>#1362에서 이 클래스째 지운다.</b>
     *
     * <p>#1360이 응답에서 {@code items}를 빼면서 이미 열려 있던 탭이 통째로 깨졌다 — 이 앱의
     * 서비스워커는 새 버전을 사용자가 받아들여야 갈고(`registerType: "prompt"`), 앱 셸이
     * precache라 서버에서 파일이 사라져도 옛 번들은 계속 돈다.
     */
    @Nested
    @DisplayName("옛 클라이언트 호환")
    class LegacyItems {

        @Test
        @DisplayName("items는 묶음을 편 것과 같다 — 개수도 순서도")
        void flattensSectionsIntoItems() throws Exception {
            create("""
                    {"category": "BAG", "title": "충전기", "sectionLabel": "캐리어"}""");
            create("""
                    {"category": "BAG", "title": "여권 지갑"}""");
            create("""
                    {"category": "BAG", "title": "옷", "sectionLabel": "캐리어"}""");

            getPrep()
                    .andExpect(jsonPath("$.data.groups[2].items", hasSize(3)))
                    // 묶음을 편 순서 그대로다 — 묶음 없음이 먼저다.
                    .andExpect(jsonPath("$.data.groups[2].items[0].title").value("여권 지갑"))
                    .andExpect(jsonPath("$.data.groups[2].items[1].title").value("충전기"))
                    .andExpect(jsonPath("$.data.groups[2].items[2].title").value("옷"));
        }

        @Test
        @DisplayName("빈 분류는 빈 배열이다 — 옛 번들이 map을 도는 자리다")
        void emptyCategoryKeepsEmptyArray() throws Exception {
            getPrep()
                    .andExpect(jsonPath("$.data.groups[0].items", hasSize(0)))
                    .andExpect(jsonPath("$.data.groups[0].sections", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("삭제 · 순서")
    class DeleteAndOrder {

        @Test
        @DisplayName("삭제는 하드 삭제다 — 되돌리기는 FE의 5초 대기가 한다")
        void deletesHard() throws Exception {
            long id = createAndGetId("""
                    {"title": "환전"}""");

            mockMvc.perform(delete("/api/travel/prep/items/" + id)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            assertThat(prepRepository.findById(id)).isEmpty();
            getPrep().andExpect(jsonPath("$.data.total").value(0));
        }

        @Test
        @DisplayName("한 분류의 순서를 통째로 다시 매긴다")
        void reordersCategory() throws Exception {
            long first = createAndGetId("""
                    {"category": "BAG", "title": "멀티어댑터"}""");
            long second = createAndGetId("""
                    {"category": "BAG", "title": "양말"}""");
            long third = createAndGetId("""
                    {"category": "BAG", "title": "충전기"}""");

            mockMvc.perform(put("/api/travel/trips/" + tripId + "/prep/order")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"category": "BAG", "itemIds": [%d, %d, %d]}"""
                                    .formatted(third, first, second)))
                    .andExpect(status().isOk());

            getPrep()
                    .andExpect(jsonPath("$.data.groups[2].sections[0].items[0].title").value("충전기"))
                    .andExpect(jsonPath("$.data.groups[2].sections[0].items[1].title").value("멀티어댑터"))
                    .andExpect(jsonPath("$.data.groups[2].sections[0].items[2].title").value("양말"));
        }

        @Test
        @DisplayName("다른 분류의 항목을 섞어 보내면 404 — 분류 이동은 PATCH가 한다")
        void rejectsItemFromAnotherCategory() throws Exception {
            long bag = createAndGetId("""
                    {"category": "BAG", "title": "양말"}""");
            long todo = createAndGetId("""
                    {"category": "TODO", "title": "환전"}""");

            mockMvc.perform(put("/api/travel/trips/" + tripId + "/prep/order")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"category": "BAG", "itemIds": [%d, %d]}"""
                                    .formatted(bag, todo)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRAVEL-ERR-022"));
        }

        @Test
        @DisplayName("빠뜨린 항목은 지워지지 않고 뒤로 밀린다")
        void keepsUnlistedItems() throws Exception {
            long first = createAndGetId("""
                    {"category": "BAG", "title": "멀티어댑터"}""");
            long second = createAndGetId("""
                    {"category": "BAG", "title": "양말"}""");

            mockMvc.perform(put("/api/travel/trips/" + tripId + "/prep/order")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"category": "BAG", "itemIds": [%d]}""".formatted(second)))
                    .andExpect(status().isOk());

            getPrep()
                    .andExpect(jsonPath("$.data.groups[2].sections[0].items", hasSize(2)))
                    .andExpect(jsonPath("$.data.groups[2].sections[0].items[0].title").value("양말"))
                    .andExpect(jsonPath("$.data.groups[2].sections[0].items[1].title").value("멀티어댑터"));
            assertThat(prepRepository.findById(first)).isPresent();
        }

        @Test
        @DisplayName("여행을 지우면 준비 목록도 함께 사라진다")
        void cascadesOnTripDelete() throws Exception {
            long id = createAndGetId("""
                    {"title": "환전"}""");

            mockMvc.perform(delete("/api/travel/trips/" + tripId)
                            .header(HttpHeaders.AUTHORIZATION, authHeader))
                    .andExpect(status().isOk());

            assertThat(prepRepository.findById(id)).isEmpty();
        }
    }

    // ---------------- helpers ----------------

    private ResultActions getPrep() throws Exception {
        return mockMvc.perform(get("/api/travel/trips/" + tripId + "/prep")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk());
    }

    private ResultActions create(String body) throws Exception {
        return mockMvc.perform(post("/api/travel/trips/" + tripId + "/prep/items")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private long createAndGetId(String body) throws Exception {
        String response = create(body).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.item.id")).longValue();
    }

    private ResultActions patchItem(long itemId, String body) throws Exception {
        return mockMvc.perform(patch("/api/travel/prep/items/" + itemId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private ResultActions check(long itemId, boolean done) throws Exception {
        return patchItem(itemId, """
                {"done": %s}""".formatted(done));
    }

    private long createTrip(String startDate, String endDate, int days) throws Exception {
        String body = mockMvc.perform(post("/api/travel/trips")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "일본 가을", "startDate": "%s",
                                 "endDate": "%s", %s}
                                """.formatted(startDate, endDate,
                                TravelCityFixture.singleLeg(osaka, days))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 기간만 통째로 옮긴다. 일수가 같아 잘리는 일정이 없으므로 확인이 필요 없다. */
    private void updateTripPeriod(String startDate, String endDate) throws Exception {
        mockMvc.perform(put("/api/travel/trips/" + tripId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "일본 가을", "startDate": "%s",
                                 "endDate": "%s", %s}
                                """.formatted(startDate, endDate,
                                TravelCityFixture.singleLeg(osaka, 4))))
                .andExpect(status().isOk());
    }
}
