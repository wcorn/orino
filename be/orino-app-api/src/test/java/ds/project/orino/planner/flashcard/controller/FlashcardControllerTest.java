package ds.project.orino.planner.flashcard.controller;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.repository.FlashcardRepository;
import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FlashcardControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudyMaterialRepository studyMaterialRepository;

    @Autowired
    private FlashcardRepository flashcardRepository;

    @Autowired
    private ReviewScheduleRepository reviewScheduleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DbCleaner dbCleaner;

    @Autowired
    private Clock clock;

    private Member member;
    private Member otherMember;
    private StudyMaterial material;
    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        member = memberRepository.save(MemberFixture.create());
        otherMember = memberRepository.save(MemberFixture.create("other", "password"));
        material = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "자료", MaterialType.BOOK));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    @Test
    @DisplayName("POST - 카드 생성 + 첫 복습(sequence=1, today+1, 1일, 2.50) 자동 생성")
    void create_with_first_review() throws Exception {
        LocalDate today = testToday(clock);

        mockMvc.perform(post("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"front":"Q","back":"A"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.flashcard.front").value("Q"))
                .andExpect(jsonPath("$.data.flashcard.back").value("A"))
                .andExpect(jsonPath("$.data.flashcard.materialId").value(material.getId()))
                .andExpect(jsonPath("$.data.firstReview.sequence").value(1))
                .andExpect(jsonPath("$.data.firstReview.intervalDays").value(1))
                .andExpect(jsonPath("$.data.firstReview.easeFactor").value(2.50))
                .andExpect(jsonPath("$.data.firstReview.status").value("PENDING"))
                .andExpect(jsonPath("$.data.firstReview.scheduledAt",
                        startsWith(today.plusDays(1) + "T04:00")));
    }

    @Test
    @DisplayName("POST - front 빈 문자열이면 400")
    void create_blank_front() throws Exception {
        mockMvc.perform(post("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"front":"","back":"A"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST - front 1001자면 400")
    void create_too_long_front() throws Exception {
        String tooLong = "a".repeat(1001);
        mockMvc.perform(post("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"front\":\"" + tooLong + "\",\"back\":\"A\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST - 타인 자료에 카드 생성 시 404")
    void create_on_other_material_returns_404() throws Exception {
        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));

        mockMvc.perform(post("/api/planner/materials/{id}/flashcards", otherMaterial.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"front":"Q","back":"A"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET - 카드 목록은 생성순으로 정렬되고 nextReview 포함")
    void list_returns_cards_in_creation_order_with_next_review() throws Exception {
        Flashcard card1 = flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "Q1", "A1"));
        Flashcard card2 = flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "Q2", "A2"));
        LocalDate today = testToday(clock);
        reviewScheduleRepository.save(
                new ReviewSchedule(member.getId(), card1.getId(), 1, atTestZone(today.plusDays(1).atTime(4, 0)), 1,
                        new BigDecimal("2.50")));
        reviewScheduleRepository.save(
                new ReviewSchedule(member.getId(), card2.getId(), 1, atTestZone(today.plusDays(3).atTime(4, 0)), 1,
                        new BigDecimal("2.50")));

        mockMvc.perform(get("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flashcards", hasSize(2)))
                .andExpect(jsonPath("$.data.flashcards[0].id").value(card1.getId()))
                .andExpect(jsonPath("$.data.flashcards[0].nextReview.sequence").value(1))
                .andExpect(jsonPath("$.data.flashcards[0].nextReview.scheduledAt",
                        startsWith(today.plusDays(1) + "T04:00")))
                .andExpect(jsonPath("$.data.flashcards[1].id").value(card2.getId()))
                .andExpect(jsonPath("$.data.flashcards[1].nextReview.scheduledAt",
                        startsWith(today.plusDays(3) + "T04:00")));
    }

    @Test
    @DisplayName("GET - 가장 가까운 PENDING이 nextReview로 선택된다 (COMPLETED 무시)")
    void list_next_review_picks_earliest_pending() throws Exception {
        Flashcard card = flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "Q", "A"));
        LocalDate today = testToday(clock);
        jdbcTemplate.update("""
                INSERT INTO review_schedule
                  (member_id, flashcard_id, sequence, scheduled_at, interval_days,
                   ease_factor, status, completed_at, created_at, updated_at)
                VALUES (?, ?, 1, ?, 1, 2.50, 'COMPLETED', NOW(6), NOW(6), NOW(6))
                """, member.getId(), card.getId(), today.minusDays(5).atStartOfDay());
        reviewScheduleRepository.save(
                new ReviewSchedule(member.getId(), card.getId(), 3, atTestZone(today.plusDays(10).atTime(4, 0)), 10,
                        new BigDecimal("2.50")));
        reviewScheduleRepository.save(
                new ReviewSchedule(member.getId(), card.getId(), 2, atTestZone(today.plusDays(2).atTime(4, 0)), 2,
                        new BigDecimal("2.50")));

        mockMvc.perform(get("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flashcards[0].nextReview.sequence").value(2))
                .andExpect(jsonPath("$.data.flashcards[0].nextReview.scheduledAt",
                        startsWith(today.plusDays(2) + "T04:00")));
    }

    @Test
    @DisplayName("GET - PENDING 복습이 없는 카드는 nextReview가 null/omit")
    void list_card_without_pending_review() throws Exception {
        flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "Q", "A"));

        mockMvc.perform(get("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flashcards[0].nextReview").doesNotExist());
    }

    @Test
    @DisplayName("GET - 타인 자료 카드 목록 조회 시 404")
    void list_other_material_returns_404() throws Exception {
        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));

        mockMvc.perform(get("/api/planner/materials/{id}/flashcards", otherMaterial.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH - front/back 부분 수정, 복습 일정에 영향 없음")
    void update_does_not_affect_reviews() throws Exception {
        Flashcard card = flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "Q", "A"));
        LocalDate today = testToday(clock);
        ReviewSchedule review = reviewScheduleRepository.save(
                new ReviewSchedule(member.getId(), card.getId(), 1, atTestZone(today.plusDays(1).atTime(4, 0)), 1,
                        new BigDecimal("2.50")));

        mockMvc.perform(patch("/api/planner/flashcards/{id}", card.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"front":"Q2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.front").value("Q2"))
                .andExpect(jsonPath("$.data.back").value("A"));

        ReviewSchedule unchanged = reviewScheduleRepository.findById(review.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(unchanged.getScheduledAt())
                .isEqualTo(atTestZone(today.plusDays(1).atTime(4, 0)));
        org.assertj.core.api.Assertions.assertThat(unchanged.getSequence()).isEqualTo(1);
    }

    @Test
    @DisplayName("PATCH - 본문이 모두 null이면 400")
    void update_empty_body_returns_400() throws Exception {
        Flashcard card = flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "Q", "A"));

        mockMvc.perform(patch("/api/planner/flashcards/{id}", card.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH - 타인 카드 수정 시 404")
    void update_other_card_returns_404() throws Exception {
        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));
        Flashcard otherCard = flashcardRepository.save(
                new Flashcard(otherMember.getId(), otherMaterial.getId(), "Q", "A"));

        mockMvc.perform(patch("/api/planner/flashcards/{id}", otherCard.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"front":"X"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE - 카드 삭제 시 관련 review_schedule이 cascade 삭제된다")
    void delete_cascades_reviews() throws Exception {
        Flashcard card = flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "Q", "A"));
        LocalDate today = testToday(clock);
        reviewScheduleRepository.save(
                new ReviewSchedule(member.getId(), card.getId(), 1, atTestZone(today.plusDays(1).atTime(4, 0)), 1,
                        new BigDecimal("2.50")));
        reviewScheduleRepository.save(
                new ReviewSchedule(member.getId(), card.getId(), 2, atTestZone(today.plusDays(7).atTime(4, 0)), 7,
                        new BigDecimal("2.50")));

        mockMvc.perform(delete("/api/planner/flashcards/{id}", card.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNoContent());

        Integer cardCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flashcard WHERE id = ?", Integer.class, card.getId());
        Integer reviewCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_schedule WHERE flashcard_id = ?", Integer.class, card.getId());

        org.assertj.core.api.Assertions.assertThat(cardCount).isZero();
        org.assertj.core.api.Assertions.assertThat(reviewCount).isZero();
    }

    @Test
    @DisplayName("DELETE - 타인 카드 삭제 시 404")
    void delete_other_card_returns_404() throws Exception {
        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));
        Flashcard otherCard = flashcardRepository.save(
                new Flashcard(otherMember.getId(), otherMaterial.getId(), "Q", "A"));

        mockMvc.perform(delete("/api/planner/flashcards/{id}", otherCard.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound());
    }

    // ===== 순서 카드(ORDERING) =====

    private static final String ORDERING_ITEMS_3 = """
            [{"id":"a","text":"1단계"},{"id":"b","text":"2단계"},{"id":"c","text":"3단계"}]
            """;

    @Test
    @DisplayName("POST ORDERING - 순서 카드 생성 + 첫 복습이 종류 무관하게 자동 생성")
    void create_ordering_with_first_review() throws Exception {
        LocalDate today = testToday(clock);

        mockMvc.perform(post("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ORDERING","front":"순서대로 배열",
                                 "items":[{"id":"a","text":"1단계"},{"id":"b","text":"2단계"},
                                          {"id":"c","text":"3단계"}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.flashcard.type").value("ORDERING"))
                .andExpect(jsonPath("$.data.flashcard.front").value("순서대로 배열"))
                .andExpect(jsonPath("$.data.flashcard.back").doesNotExist())
                .andExpect(jsonPath("$.data.flashcard.items", hasSize(3)))
                .andExpect(jsonPath("$.data.flashcard.items[0].id").value("a"))
                .andExpect(jsonPath("$.data.flashcard.items[0].text").value("1단계"))
                .andExpect(jsonPath("$.data.flashcard.items[2].id").value("c"))
                // 첫 복습은 flashcard_id만 참조 → 종류 무관하게 동일하게 생성
                .andExpect(jsonPath("$.data.firstReview.sequence").value(1))
                .andExpect(jsonPath("$.data.firstReview.intervalDays").value(1))
                .andExpect(jsonPath("$.data.firstReview.scheduledAt",
                        startsWith(today.plusDays(1) + "T04:00")));
    }

    @Test
    @DisplayName("POST ORDERING - items 3개 미만이면 400")
    void create_ordering_too_few_items() throws Exception {
        mockMvc.perform(post("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ORDERING","front":"F",
                                 "items":[{"id":"a","text":"1"},{"id":"b","text":"2"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SP-ERR-002"));
    }

    @Test
    @DisplayName("POST ORDERING - items 7개 초과면 400")
    void create_ordering_too_many_items() throws Exception {
        StringBuilder items = new StringBuilder("[");
        for (int i = 0; i < 8; i++) {
            if (i > 0) {
                items.append(",");
            }
            items.append("{\"id\":\"i").append(i).append("\",\"text\":\"t").append(i).append("\"}");
        }
        items.append("]");

        mockMvc.perform(post("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ORDERING\",\"front\":\"F\",\"items\":" + items + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SP-ERR-002"));
    }

    @Test
    @DisplayName("POST ORDERING - id가 카드 내에서 중복이면 400")
    void create_ordering_duplicate_id() throws Exception {
        mockMvc.perform(post("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ORDERING","front":"F",
                                 "items":[{"id":"a","text":"1"},{"id":"a","text":"2"},{"id":"c","text":"3"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SP-ERR-002"));
    }

    @Test
    @DisplayName("POST ORDERING - 항목 text가 빈 문자열이면 400")
    void create_ordering_blank_item_text() throws Exception {
        mockMvc.perform(post("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ORDERING","front":"F",
                                 "items":[{"id":"a","text":""},{"id":"b","text":"2"},{"id":"c","text":"3"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SP-ERR-002"));
    }

    @Test
    @DisplayName("POST BASIC - back이 없으면 400 (BASIC은 back 필수)")
    void create_basic_without_back() throws Exception {
        mockMvc.perform(post("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"BASIC","front":"Q"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SP-ERR-002"));
    }

    @Test
    @DisplayName("POST - type 생략 시 BASIC으로 저장된다")
    void create_defaults_to_basic() throws Exception {
        mockMvc.perform(post("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"front":"Q","back":"A"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.flashcard.type").value("BASIC"))
                .andExpect(jsonPath("$.data.flashcard.items").doesNotExist());
    }

    @Test
    @DisplayName("GET - 순서 카드 목록은 type/items를 포함하고 back은 생략된다")
    void list_includes_type_and_items() throws Exception {
        flashcardRepository.save(
                Flashcard.ordering(member.getId(), material.getId(), "순서", ORDERING_ITEMS_3.strip()));

        mockMvc.perform(get("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flashcards[0].type").value("ORDERING"))
                .andExpect(jsonPath("$.data.flashcards[0].items", hasSize(3)))
                .andExpect(jsonPath("$.data.flashcards[0].items[1].text").value("2단계"))
                .andExpect(jsonPath("$.data.flashcards[0].back").doesNotExist());
    }

    @Test
    @DisplayName("PATCH ORDERING - 항목 재정렬이 저장 순서에 반영된다")
    void update_ordering_reorder_items() throws Exception {
        Flashcard card = flashcardRepository.save(
                Flashcard.ordering(member.getId(), material.getId(), "순서", ORDERING_ITEMS_3.strip()));

        mockMvc.perform(patch("/api/planner/flashcards/{id}", card.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"id":"c","text":"3단계"},{"id":"b","text":"2단계"},
                                          {"id":"a","text":"1단계"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("ORDERING"))
                .andExpect(jsonPath("$.data.items[0].id").value("c"))
                .andExpect(jsonPath("$.data.items[2].id").value("a"));
    }

    @Test
    @DisplayName("PATCH - BASIC → ORDERING 전환 (items 지정, back은 비워짐)")
    void update_convert_basic_to_ordering() throws Exception {
        Flashcard card = flashcardRepository.save(
                new Flashcard(member.getId(), material.getId(), "Q", "A"));

        mockMvc.perform(patch("/api/planner/flashcards/{id}", card.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ORDERING\",\"items\":" + ORDERING_ITEMS_3.strip() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("ORDERING"))
                .andExpect(jsonPath("$.data.items", hasSize(3)))
                .andExpect(jsonPath("$.data.back").doesNotExist());
    }

    @Test
    @DisplayName("PATCH - ORDERING → BASIC 전환 (back 지정, items는 비워짐)")
    void update_convert_ordering_to_basic() throws Exception {
        Flashcard card = flashcardRepository.save(
                Flashcard.ordering(member.getId(), material.getId(), "순서", ORDERING_ITEMS_3.strip()));

        mockMvc.perform(patch("/api/planner/flashcards/{id}", card.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"BASIC","back":"정답"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("BASIC"))
                .andExpect(jsonPath("$.data.back").value("정답"))
                .andExpect(jsonPath("$.data.items").doesNotExist());
    }

    // ===== 양방향 짝 카드(Bidirectional) =====

    @Test
    @DisplayName("POST bidirectional - 앞↔뒤 2장 생성 + 같은 siblingGroupId + 첫 복습 엇갈림(A+1, B+2)")
    void create_bidirectional_pair() throws Exception {
        LocalDate today = testToday(clock);

        String body = mockMvc.perform(post("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"front":"쿠버네티스","back":"컨테이너 오케스트레이션","bidirectional":true}
                                """))
                .andExpect(status().isCreated())
                // A: front→back, siblingGroupId = A.id, 첫 복습 today+1
                .andExpect(jsonPath("$.data.flashcard.front").value("쿠버네티스"))
                .andExpect(jsonPath("$.data.flashcard.back").value("컨테이너 오케스트레이션"))
                .andExpect(jsonPath("$.data.flashcard.siblingGroupId").exists())
                .andExpect(jsonPath("$.data.firstReview.scheduledAt",
                        startsWith(today.plusDays(1) + "T04:00")))
                .andExpect(jsonPath("$.data.firstReview.intervalDays").value(1))
                // sibling B: front/back 뒤집힘, 같은 그룹, 첫 복습 today+2
                .andExpect(jsonPath("$.data.sibling.flashcard.front").value("컨테이너 오케스트레이션"))
                .andExpect(jsonPath("$.data.sibling.flashcard.back").value("쿠버네티스"))
                .andExpect(jsonPath("$.data.sibling.firstReview.scheduledAt",
                        startsWith(today.plusDays(2) + "T04:00")))
                .andExpect(jsonPath("$.data.sibling.firstReview.intervalDays").value(1))
                .andReturn().getResponse().getContentAsString();

        Number groupA = com.jayway.jsonpath.JsonPath.read(body, "$.data.flashcard.siblingGroupId");
        Number groupB = com.jayway.jsonpath.JsonPath.read(body, "$.data.sibling.flashcard.siblingGroupId");
        org.assertj.core.api.Assertions.assertThat(groupA.longValue())
                .isEqualTo(groupB.longValue());

        // 실제로 2장 + 2 review 저장
        org.assertj.core.api.Assertions.assertThat(flashcardRepository.findAll()).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(reviewScheduleRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("POST bidirectional - ORDERING + bidirectional이면 400")
    void create_bidirectional_ordering_returns_400() throws Exception {
        mockMvc.perform(post("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ORDERING","front":"F","bidirectional":true,
                                 "items":[{"id":"a","text":"1"},{"id":"b","text":"2"},{"id":"c","text":"3"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SP-ERR-002"));
    }

    @Test
    @DisplayName("POST bidirectional - back 없으면 400")
    void create_bidirectional_without_back_returns_400() throws Exception {
        mockMvc.perform(post("/api/planner/materials/{id}/flashcards", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"front":"Q","bidirectional":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SP-ERR-002"));
    }

    @Test
    @DisplayName("PATCH - BASIC → ORDERING 전환인데 items 미지정이면 400")
    void update_convert_to_ordering_without_items_fails() throws Exception {
        Flashcard card = flashcardRepository.save(
                new Flashcard(member.getId(), material.getId(), "Q", "A"));

        mockMvc.perform(patch("/api/planner/flashcards/{id}", card.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ORDERING"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SP-ERR-002"));
    }
}
