package ds.project.orino.planner.material.controller;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.unit.entity.StudyUnit;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudyUnitControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudyMaterialRepository studyMaterialRepository;

    @Autowired
    private StudyUnitRepository studyUnitRepository;

    private Member member;
    private String accessToken;
    private StudyMaterial material;

    @BeforeEach
    void setUp() throws Exception {
        studyUnitRepository.deleteAll();
        studyMaterialRepository.deleteAll();
        memberRepository.deleteAll();

        member = memberRepository.save(MemberFixture.create());
        accessToken = AuthFixture.loginAndGetAccessToken(mockMvc);
        material = studyMaterialRepository.save(StudyMaterialFixture.create(member.getId()));
    }

    @Test
    @DisplayName("POST /api/planner/materials/{materialId}/units - 단건 추가, sort_order=1로 시작한다")
    void create_single() throws Exception {
        mockMvc.perform(post("/api/planner/materials/{materialId}/units", material.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"units": [{"title": "아이템 1"}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.units.length()").value(1))
                .andExpect(jsonPath("$.data.units[0].title").value("아이템 1"))
                .andExpect(jsonPath("$.data.units[0].materialId").value(material.getId()))
                .andExpect(jsonPath("$.data.units[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data.units[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/planner/materials/{materialId}/units - 배열 추가, sort_order가 순차 부여된다")
    void create_batch_sortOrderSequential() throws Exception {
        mockMvc.perform(post("/api/planner/materials/{materialId}/units", material.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"units": [
                                    {"title": "아이템 1"},
                                    {"title": "아이템 2"},
                                    {"title": "아이템 3"}
                                ]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.units.length()").value(3))
                .andExpect(jsonPath("$.data.units[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data.units[1].sortOrder").value(2))
                .andExpect(jsonPath("$.data.units[2].sortOrder").value(3));
    }

    @Test
    @DisplayName("POST /api/planner/materials/{materialId}/units - 기존 단위가 있으면 max+1부터 부여된다")
    void create_continuesFromMaxSortOrder() throws Exception {
        studyUnitRepository.save(StudyMaterialFixture.createUnit(member.getId(), material.getId(), 5));

        mockMvc.perform(post("/api/planner/materials/{materialId}/units", material.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"units": [{"title": "신규 1"}, {"title": "신규 2"}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.units[0].sortOrder").value(6))
                .andExpect(jsonPath("$.data.units[1].sortOrder").value(7));
    }

    @Test
    @DisplayName("POST /api/planner/materials/{materialId}/units - 빈 배열은 400을 반환한다")
    void create_emptyArray_returns400() throws Exception {
        mockMvc.perform(post("/api/planner/materials/{materialId}/units", material.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"units": []}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/planner/materials/{materialId}/units - 단위 제목이 빈 문자열이면 400을 반환한다")
    void create_blankTitle_returns400() throws Exception {
        mockMvc.perform(post("/api/planner/materials/{materialId}/units", material.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"units": [{"title": ""}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/planner/materials/{materialId}/units - 다른 멤버 자료에 추가 시 404를 반환한다")
    void create_otherMembersMaterial_returns404() throws Exception {
        Member another = memberRepository.save(MemberFixture.create("other", "password"));
        StudyMaterial othersMaterial = studyMaterialRepository.save(
                StudyMaterialFixture.create(another.getId()));

        mockMvc.perform(post("/api/planner/materials/{materialId}/units", othersMaterial.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"units": [{"title": "해킹"}]}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));
    }

    @Test
    @DisplayName("PATCH /api/planner/units/{id} - title만 부분 업데이트")
    void update_title() throws Exception {
        StudyUnit unit = studyUnitRepository.save(
                StudyMaterialFixture.createUnit(member.getId(), material.getId(), 1));

        mockMvc.perform(patch("/api/planner/units/{id}", unit.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "수정된 단위"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 단위"))
                .andExpect(jsonPath("$.data.sortOrder").value(1));
    }

    @Test
    @DisplayName("PATCH /api/planner/units/{id} - sortOrder만 부분 업데이트")
    void update_sortOrder() throws Exception {
        StudyUnit unit = studyUnitRepository.save(
                StudyMaterialFixture.createUnit(member.getId(), material.getId(), 1));

        mockMvc.perform(patch("/api/planner/units/{id}", unit.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sortOrder": 5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(5));
    }

    @Test
    @DisplayName("PATCH /api/planner/units/{id} - 다른 멤버 단위 수정 시 404")
    void update_otherMembers_returns404() throws Exception {
        Member another = memberRepository.save(MemberFixture.create("other", "password"));
        StudyMaterial othersMaterial = studyMaterialRepository.save(
                StudyMaterialFixture.create(another.getId()));
        StudyUnit othersUnit = studyUnitRepository.save(
                StudyMaterialFixture.createUnit(another.getId(), othersMaterial.getId(), 1));

        mockMvc.perform(patch("/api/planner/units/{id}", othersUnit.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "해킹"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));
    }

    @Test
    @DisplayName("DELETE /api/planner/units/{id} - 단위를 삭제하고 204를 반환한다")
    void delete_success() throws Exception {
        StudyUnit unit = studyUnitRepository.save(
                StudyMaterialFixture.createUnit(member.getId(), material.getId(), 1));

        mockMvc.perform(delete("/api/planner/units/{id}", unit.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        assertThat(studyUnitRepository.findById(unit.getId())).isEmpty();
    }

    @Test
    @DisplayName("DELETE /api/planner/units/{id} - 다른 멤버 단위 삭제 시 404")
    void delete_otherMembers_returns404() throws Exception {
        Member another = memberRepository.save(MemberFixture.create("other", "password"));
        StudyMaterial othersMaterial = studyMaterialRepository.save(
                StudyMaterialFixture.create(another.getId()));
        StudyUnit othersUnit = studyUnitRepository.save(
                StudyMaterialFixture.createUnit(another.getId(), othersMaterial.getId(), 1));

        mockMvc.perform(delete("/api/planner/units/{id}", othersUnit.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));

        assertThat(studyUnitRepository.findById(othersUnit.getId())).isPresent();
    }

    @Test
    @DisplayName("POST /api/planner/materials/{materialId}/units - 인증 없으면 403")
    void create_noAuth() throws Exception {
        mockMvc.perform(post("/api/planner/materials/{materialId}/units", material.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"units": [{"title": "아이템"}]}
                                """))
                .andExpect(status().isForbidden());
    }
}
