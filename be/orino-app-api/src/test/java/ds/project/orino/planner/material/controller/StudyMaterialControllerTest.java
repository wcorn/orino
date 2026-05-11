package ds.project.orino.planner.material.controller;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.material.entity.MaterialStatus;
import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.unit.entity.StudyUnit;
import ds.project.orino.domain.planner.unit.entity.UnitStatus;
import ds.project.orino.domain.planner.unit.repository.StudyUnitRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.StudyMaterialFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
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
    private StudyUnitRepository studyUnitRepository;

    private Member member;
    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        studyUnitRepository.deleteAll();
        studyMaterialRepository.deleteAll();
        memberRepository.deleteAll();

        member = memberRepository.save(MemberFixture.create());
        accessToken = AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    @Test
    @DisplayName("POST /api/planner/materials - 자료를 생성하고 201을 반환한다")
    void create_success() throws Exception {
        mockMvc.perform(post("/api/planner/materials")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "이펙티브 자바", "type": "BOOK"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.title").value("이펙티브 자바"))
                .andExpect(jsonPath("$.data.type").value("BOOK"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.totalUnits").value(0))
                .andExpect(jsonPath("$.data.completedUnits").value(0));
    }

    @Test
    @DisplayName("POST /api/planner/materials - 제목이 비어있으면 400을 반환한다")
    void create_blankTitle() throws Exception {
        mockMvc.perform(post("/api/planner/materials")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "", "type": "BOOK"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/planner/materials - 제목이 200자를 초과하면 400을 반환한다")
    void create_tooLongTitle() throws Exception {
        String longTitle = "a".repeat(201);
        mockMvc.perform(post("/api/planner/materials")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "type": "BOOK"}
                                """.formatted(longTitle)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/planner/materials - 유효하지 않은 type이면 400을 반환한다")
    void create_invalidType() throws Exception {
        mockMvc.perform(post("/api/planner/materials")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "이펙티브 자바", "type": "UNKNOWN"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/planner/materials - 인증 없으면 403을 반환한다")
    void create_noAuth() throws Exception {
        mockMvc.perform(post("/api/planner/materials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "이펙티브 자바", "type": "BOOK"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/planner/materials - 본인의 자료 목록만 반환한다")
    void list_onlyOwnMaterials() throws Exception {
        Member another = memberRepository.save(MemberFixture.create("other", "password"));
        studyMaterialRepository.save(StudyMaterialFixture.create(member.getId(), "내 자료", MaterialType.BOOK));
        studyMaterialRepository.save(StudyMaterialFixture.create(another.getId(), "타인 자료", MaterialType.LECTURE));

        mockMvc.perform(get("/api/planner/materials")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materials.length()").value(1))
                .andExpect(jsonPath("$.data.materials[0].title").value("내 자료"));
    }

    @Test
    @DisplayName("GET /api/planner/materials?status=ACTIVE - 상태로 필터링한다")
    void list_filterByStatus() throws Exception {
        studyMaterialRepository.save(StudyMaterialFixture.create(member.getId(), "활성 자료", MaterialType.BOOK));
        StudyMaterial completed = studyMaterialRepository.save(
                StudyMaterialFixture.create(member.getId(), "완료된 자료", MaterialType.LECTURE));
        completed.updateStatus(MaterialStatus.COMPLETED);
        studyMaterialRepository.save(completed);

        mockMvc.perform(get("/api/planner/materials?status=COMPLETED")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materials.length()").value(1))
                .andExpect(jsonPath("$.data.materials[0].title").value("완료된 자료"));
    }

    @Test
    @DisplayName("GET /api/planner/materials - 진행률(totalUnits/completedUnits)을 계산해 반환한다")
    void list_includesProgress() throws Exception {
        StudyMaterial material = studyMaterialRepository.save(
                StudyMaterialFixture.create(member.getId()));
        StudyUnit unit1 = studyUnitRepository.save(
                StudyMaterialFixture.createUnit(member.getId(), material.getId(), 1));
        studyUnitRepository.save(
                StudyMaterialFixture.createUnit(member.getId(), material.getId(), 2));
        studyUnitRepository.save(
                StudyMaterialFixture.createUnit(member.getId(), material.getId(), 3));
        markCompleted(unit1);

        mockMvc.perform(get("/api/planner/materials")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materials[0].totalUnits").value(3))
                .andExpect(jsonPath("$.data.materials[0].completedUnits").value(1));
    }

    @Test
    @DisplayName("GET /api/planner/materials/{id} - 자료 상세와 단위 목록을 반환한다")
    void detail_success() throws Exception {
        StudyMaterial material = studyMaterialRepository.save(
                StudyMaterialFixture.create(member.getId()));
        studyUnitRepository.save(StudyMaterialFixture.createUnit(member.getId(), material.getId(), 1));
        studyUnitRepository.save(StudyMaterialFixture.createUnit(member.getId(), material.getId(), 2));

        mockMvc.perform(get("/api/planner/materials/{id}", material.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(material.getId()))
                .andExpect(jsonPath("$.data.totalUnits").value(2))
                .andExpect(jsonPath("$.data.completedUnits").value(0))
                .andExpect(jsonPath("$.data.units.length()").value(2))
                .andExpect(jsonPath("$.data.units[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data.units[1].sortOrder").value(2));
    }

    @Test
    @DisplayName("GET /api/planner/materials/{id} - 다른 멤버의 자료 조회 시 404를 반환한다")
    void detail_otherMembers_returns404() throws Exception {
        Member another = memberRepository.save(MemberFixture.create("other", "password"));
        StudyMaterial othersMaterial = studyMaterialRepository.save(
                StudyMaterialFixture.create(another.getId()));

        mockMvc.perform(get("/api/planner/materials/{id}", othersMaterial.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));
    }

    @Test
    @DisplayName("GET /api/planner/materials/{id} - 존재하지 않는 자료는 404를 반환한다")
    void detail_notFound() throws Exception {
        mockMvc.perform(get("/api/planner/materials/{id}", 99999L)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));
    }

    @Test
    @DisplayName("PATCH /api/planner/materials/{id} - title만 부분 업데이트한다")
    void update_partialTitle() throws Exception {
        StudyMaterial material = studyMaterialRepository.save(
                StudyMaterialFixture.create(member.getId()));

        mockMvc.perform(patch("/api/planner/materials/{id}", material.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "수정된 제목"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 제목"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("PATCH /api/planner/materials/{id} - status만 부분 업데이트한다")
    void update_partialStatus() throws Exception {
        StudyMaterial material = studyMaterialRepository.save(
                StudyMaterialFixture.create(member.getId()));
        String originalTitle = material.getTitle();

        mockMvc.perform(patch("/api/planner/materials/{id}", material.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "COMPLETED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.title").value(originalTitle));
    }

    @Test
    @DisplayName("PATCH /api/planner/materials/{id} - 다른 멤버 자료 수정 시 404를 반환한다")
    void update_otherMembers_returns404() throws Exception {
        Member another = memberRepository.save(MemberFixture.create("other", "password"));
        StudyMaterial othersMaterial = studyMaterialRepository.save(
                StudyMaterialFixture.create(another.getId()));

        mockMvc.perform(patch("/api/planner/materials/{id}", othersMaterial.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "해킹"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));
    }

    @Test
    @DisplayName("DELETE /api/planner/materials/{id} - 자료를 삭제하고 204를 반환한다")
    void delete_success() throws Exception {
        StudyMaterial material = studyMaterialRepository.save(
                StudyMaterialFixture.create(member.getId()));

        mockMvc.perform(delete("/api/planner/materials/{id}", material.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        assertThat(studyMaterialRepository.findById(material.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /api/planner/materials/{id} - 단위가 있으면 cascade로 함께 삭제된다")
    void delete_cascadesUnits() throws Exception {
        StudyMaterial material = studyMaterialRepository.save(
                StudyMaterialFixture.create(member.getId()));
        StudyUnit unit = studyUnitRepository.save(
                StudyMaterialFixture.createUnit(member.getId(), material.getId(), 1));

        mockMvc.perform(delete("/api/planner/materials/{id}", material.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        assertThat(studyUnitRepository.findById(unit.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /api/planner/materials/{id} - 다른 멤버 자료 삭제 시 404를 반환한다")
    void delete_otherMembers_returns404() throws Exception {
        Member another = memberRepository.save(MemberFixture.create("other", "password"));
        StudyMaterial othersMaterial = studyMaterialRepository.save(
                StudyMaterialFixture.create(another.getId()));

        mockMvc.perform(delete("/api/planner/materials/{id}", othersMaterial.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));

        assertThat(studyMaterialRepository.findById(othersMaterial.getId())).isPresent();
    }

    private void markCompleted(StudyUnit unit) {
        try {
            Field statusField = StudyUnit.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(unit, UnitStatus.COMPLETED);
            Field completedAtField = StudyUnit.class.getDeclaredField("completedAt");
            completedAtField.setAccessible(true);
            completedAtField.set(unit, LocalDateTime.now());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        studyUnitRepository.save(unit);
    }
}
