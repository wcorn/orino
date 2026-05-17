package ds.project.orino.planner.material.controller;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.material.entity.MaterialStatus;
import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.note.entity.Note;
import ds.project.orino.domain.planner.note.repository.NoteRepository;
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

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudyMaterialControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudyMaterialRepository studyMaterialRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DbCleaner dbCleaner;

    private Member member;
    private Member otherMember;
    private String accessToken;
    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        member = memberRepository.save(MemberFixture.create());
        otherMember = memberRepository.save(MemberFixture.create("other", "password"));
        accessToken = AuthFixture.loginAndGetAccessToken(mockMvc);
        authHeader = "Bearer " + accessToken;
    }

    @Test
    @DisplayName("POST /api/planner/materials - 자료 생성 시 빈 노트가 함께 생성된다")
    void create_material_with_empty_note() throws Exception {
        mockMvc.perform(post("/api/planner/materials")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "이펙티브 자바", "type": "BOOK"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.material.title").value("이펙티브 자바"))
                .andExpect(jsonPath("$.data.material.type").value("BOOK"))
                .andExpect(jsonPath("$.data.material.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.material.flashcardCount").value(0))
                .andExpect(jsonPath("$.data.material.dueReviewCount").value(0))
                .andExpect(jsonPath("$.data.note.content.type").value("doc"))
                .andExpect(jsonPath("$.data.note.content.content").isArray())
                .andExpect(jsonPath("$.data.note.materialId").exists());
    }

    @Test
    @DisplayName("POST /api/planner/materials - title이 빈 문자열이면 400")
    void create_blank_title() throws Exception {
        mockMvc.perform(post("/api/planner/materials")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "", "type": "BOOK"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/planner/materials - type이 enum 외 값이면 400")
    void create_invalid_type() throws Exception {
        mockMvc.perform(post("/api/planner/materials")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "x", "type": "UNKNOWN"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/planner/materials - 본인 자료만 createdAt desc 순으로 반환한다")
    void list_returns_own_materials_desc() throws Exception {
        StudyMaterial first = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "첫째", MaterialType.BOOK));
        StudyMaterial second = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "둘째", MaterialType.LECTURE));
        studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));

        mockMvc.perform(get("/api/planner/materials")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materials", hasSize(2)))
                .andExpect(jsonPath("$.data.materials[0].id").value(second.getId()))
                .andExpect(jsonPath("$.data.materials[1].id").value(first.getId()));
    }

    @Test
    @DisplayName("GET /api/planner/materials?status=ACTIVE - 상태 필터가 동작한다")
    void list_filters_by_status() throws Exception {
        studyMaterialRepository.save(new StudyMaterial(member.getId(), "활성", MaterialType.BOOK));
        StudyMaterial done = new StudyMaterial(member.getId(), "완료", MaterialType.LECTURE);
        done.updateStatus(MaterialStatus.COMPLETED);
        studyMaterialRepository.save(done);

        mockMvc.perform(get("/api/planner/materials")
                        .queryParam("status", "COMPLETED")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materials", hasSize(1)))
                .andExpect(jsonPath("$.data.materials[0].title").value("완료"));
    }

    @Test
    @DisplayName("GET /api/planner/materials - flashcardCount, dueReviewCount가 집계된다")
    void list_aggregates_counts() throws Exception {
        StudyMaterial material = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "자료", MaterialType.BOOK));

        Long fc1 = insertFlashcard(material.getId());
        Long fc2 = insertFlashcard(material.getId());
        Long fc3 = insertFlashcard(material.getId());

        insertReview(fc1, LocalDate.now().minusDays(1), "PENDING");
        insertReview(fc2, LocalDate.now(), "PENDING");
        insertReview(fc3, LocalDate.now().plusDays(1), "PENDING");
        insertReview(fc1, LocalDate.now().minusDays(2), "COMPLETED");

        mockMvc.perform(get("/api/planner/materials")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materials[0].flashcardCount").value(3))
                .andExpect(jsonPath("$.data.materials[0].dueReviewCount").value(2));
    }

    @Test
    @DisplayName("GET /api/planner/materials/{id} - 본인 자료 상세를 반환한다")
    void detail_returns_own_material() throws Exception {
        StudyMaterial material = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "자료", MaterialType.BOOK));

        mockMvc.perform(get("/api/planner/materials/{id}", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("자료"))
                .andExpect(jsonPath("$.data.flashcardCount").value(0))
                .andExpect(jsonPath("$.data.dueReviewCount").value(0));
    }

    @Test
    @DisplayName("GET /api/planner/materials/{id} - 타인 자료 조회 시 404")
    void detail_not_owned_returns_404() throws Exception {
        StudyMaterial material = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));

        mockMvc.perform(get("/api/planner/materials/{id}", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));
    }

    @Test
    @DisplayName("PATCH /api/planner/materials/{id} - title, status 부분 수정")
    void update_partial() throws Exception {
        StudyMaterial material = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "기존", MaterialType.BOOK));

        mockMvc.perform(patch("/api/planner/materials/{id}", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "수정", "status": "COMPLETED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("PATCH /api/planner/materials/{id} - body가 모두 null이면 400")
    void update_empty_request() throws Exception {
        StudyMaterial material = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "기존", MaterialType.BOOK));

        mockMvc.perform(patch("/api/planner/materials/{id}", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/planner/materials/{id} - 자료/노트/플래시카드/복습이 모두 cascade 삭제된다")
    void delete_cascades_to_children() throws Exception {
        StudyMaterial material = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "삭제 대상", MaterialType.BOOK));
        Note note = noteRepository.save(new Note(member.getId(), material.getId()));
        Long flashcardId = insertFlashcard(material.getId());
        insertReview(flashcardId, LocalDate.now(), "PENDING");

        mockMvc.perform(delete("/api/planner/materials/{id}", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNoContent());

        Integer materialCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM study_material WHERE id = ?", Integer.class, material.getId());
        Integer noteCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM note WHERE id = ?", Integer.class, note.getId());
        Integer flashcardCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flashcard WHERE id = ?", Integer.class, flashcardId);
        Integer reviewCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_schedule WHERE flashcard_id = ?", Integer.class, flashcardId);

        assertCountZero(materialCount, "study_material");
        assertCountZero(noteCount, "note");
        assertCountZero(flashcardCount, "flashcard");
        assertCountZero(reviewCount, "review_schedule");
    }

    @Test
    @DisplayName("DELETE /api/planner/materials/{id} - 타인 자료 삭제 시도는 404")
    void delete_not_owned_returns_404() throws Exception {
        StudyMaterial material = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));

        mockMvc.perform(delete("/api/planner/materials/{id}", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound());
    }

    private Long insertFlashcard(Long materialId) {
        jdbcTemplate.update("""
                INSERT INTO flashcard (member_id, material_id, front, back, created_at, updated_at)
                VALUES (?, ?, 'q', 'a', NOW(6), NOW(6))
                """, member.getId(), materialId);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertReview(Long flashcardId, LocalDate scheduledDate, String status) {
        jdbcTemplate.update("""
                INSERT INTO review_schedule
                  (member_id, flashcard_id, sequence, scheduled_date, interval_days,
                   ease_factor, status, created_at, updated_at)
                VALUES (?, ?, 1, ?, 1, 2.50, ?, NOW(6), NOW(6))
                """, member.getId(), flashcardId, scheduledDate, status);
    }

    private static void assertCountZero(Integer count, String tableName) {
        if (count == null || count != 0) {
            throw new AssertionError(tableName + " row count should be 0 but was " + count);
        }
    }
}
