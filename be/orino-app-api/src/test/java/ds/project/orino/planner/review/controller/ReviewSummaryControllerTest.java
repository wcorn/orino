package ds.project.orino.planner.review.controller;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.repository.FlashcardRepository;
import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.entity.Rating;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.FixedClockConfig;
import ds.project.orino.support.MemberFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(FixedClockConfig.class)
class ReviewSummaryControllerTest extends ApiTestSupport {

    private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);
    private static final BigDecimal EF = new BigDecimal("2.50");

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private StudyMaterialRepository studyMaterialRepository;
    @Autowired
    private FlashcardRepository flashcardRepository;
    @Autowired
    private ReviewScheduleRepository reviewScheduleRepository;
    @Autowired
    private DbCleaner dbCleaner;

    private Member member;
    private Member otherMember;
    private StudyMaterial materialA;
    private StudyMaterial materialB;
    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        member = memberRepository.save(MemberFixture.create());
        otherMember = memberRepository.save(MemberFixture.create("other", "password"));
        materialA = studyMaterialRepository.save(new StudyMaterial(member.getId(), "이펙티브 자바", MaterialType.BOOK));
        materialB = studyMaterialRepository.save(new StudyMaterial(member.getId(), "모던 자바", MaterialType.LECTURE));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    private Flashcard card(StudyMaterial material) {
        return flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "Q", "A"));
    }

    private ReviewSchedule pending(Flashcard c, LocalDateTime scheduledLocal) {
        return reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), c.getId(), 1, atTestZone(scheduledLocal), 1, EF));
    }

    private void completed(Flashcard c, LocalDateTime completedLocal) {
        ReviewSchedule r = new ReviewSchedule(member.getId(), c.getId(), 1,
                atTestZone(TODAY.minusDays(3).atTime(4, 0)), 1, EF);
        r.complete(Rating.GOOD, atTestZone(completedLocal), TEST_ZONE);
        reviewScheduleRepository.save(r);
    }

    @Test
    @DisplayName("GET summary - counts는 목록 길이가 아니라 서버 총계(now/overdue/upcoming/doneToday)")
    void counts_are_server_totals() throws Exception {
        Flashcard a = card(materialA);
        pending(a, TODAY.minusDays(2).atTime(4, 0));  // overdue → now
        pending(a, TODAY.atTime(4, 0));               // due now
        pending(a, TODAY.atTime(20, 0));              // 오늘 남음
        Flashcard b = card(materialB);
        pending(b, TODAY.plusDays(3).atTime(4, 0));   // future
        completed(a, TODAY.atTime(9, 0));             // doneToday
        completed(a, TODAY.minusDays(1).atTime(22, 0)); // 어제 완료 → 제외

        mockMvc.perform(get("/api/planner/reviews/summary")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.today").value("2026-01-15"))
                .andExpect(jsonPath("$.data.counts.now").value(2))
                .andExpect(jsonPath("$.data.counts.overdue").value(1))
                .andExpect(jsonPath("$.data.counts.upcoming").value(4))
                .andExpect(jsonPath("$.data.counts.doneToday").value(1))
                .andExpect(jsonPath("$.data.estimatedMinutes").value(1));
    }

    @Test
    @DisplayName("GET summary - 자료별 due/overdue/nextLabel, due+overdue 내림차순 정렬")
    void materials_grouped_and_sorted() throws Exception {
        Flashcard a = card(materialA);
        pending(a, TODAY.minusDays(2).atTime(4, 0));  // A overdue
        pending(a, TODAY.atTime(4, 0));               // A due now
        pending(a, TODAY.atTime(20, 0));              // A 오늘 남음
        Flashcard b = card(materialB);
        pending(b, TODAY.plusDays(3).atTime(4, 0));   // B future only

        mockMvc.perform(get("/api/planner/reviews/summary")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materials", hasSize(2)))
                .andExpect(jsonPath("$.data.materials[0].id").value(materialA.getId()))
                .andExpect(jsonPath("$.data.materials[0].name").value("이펙티브 자바"))
                .andExpect(jsonPath("$.data.materials[0].due").value(2))
                .andExpect(jsonPath("$.data.materials[0].overdue").value(1))
                .andExpect(jsonPath("$.data.materials[0].nextLabel").value("지금"))
                .andExpect(jsonPath("$.data.materials[1].id").value(materialB.getId()))
                .andExpect(jsonPath("$.data.materials[1].due").value(0))
                .andExpect(jsonPath("$.data.materials[1].overdue").value(0))
                .andExpect(jsonPath("$.data.materials[1].nextLabel").value("01/18"));
    }

    @Test
    @DisplayName("GET summary - PENDING이 없으면 counts 0, materials 빈 배열")
    void empty_state() throws Exception {
        mockMvc.perform(get("/api/planner/reviews/summary")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.upcoming").value(0))
                .andExpect(jsonPath("$.data.counts.now").value(0))
                .andExpect(jsonPath("$.data.estimatedMinutes").value(0))
                .andExpect(jsonPath("$.data.materials", hasSize(0)));
    }

    @Test
    @DisplayName("GET summary - 타인 복습은 집계에서 제외된다")
    void excludes_other_member() throws Exception {
        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));
        Flashcard otherCard = flashcardRepository.save(
                new Flashcard(otherMember.getId(), otherMaterial.getId(), "Q2", "A2"));
        reviewScheduleRepository.save(new ReviewSchedule(
                otherMember.getId(), otherCard.getId(), 1, atTestZone(TODAY.atTime(4, 0)), 1, EF));

        mockMvc.perform(get("/api/planner/reviews/summary")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.upcoming").value(0))
                .andExpect(jsonPath("$.data.materials", hasSize(0)));
    }
}
