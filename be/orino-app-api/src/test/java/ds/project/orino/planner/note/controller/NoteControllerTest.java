package ds.project.orino.planner.note.controller;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NoteControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudyMaterialRepository studyMaterialRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private DbCleaner dbCleaner;

    private Member member;
    private Member otherMember;
    private StudyMaterial material;
    private Note note;
    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        member = memberRepository.save(MemberFixture.create());
        otherMember = memberRepository.save(MemberFixture.create("other", "password"));
        material = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "이펙티브 자바", MaterialType.BOOK));
        note = noteRepository.save(new Note(member.getId(), material.getId()));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    @Test
    @DisplayName("GET - 빈 노트 조회 시 기본 doc 구조를 반환한다")
    void get_returns_default_empty_doc() throws Exception {
        mockMvc.perform(get("/api/planner/materials/{id}/note", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(note.getId()))
                .andExpect(jsonPath("$.data.materialId").value(material.getId()))
                .andExpect(jsonPath("$.data.content.type").value("doc"))
                .andExpect(jsonPath("$.data.content.content").isArray());
    }

    private static final String HELLO_BODY = """
            {"content":{"type":"doc","content":[
              {"type":"paragraph","content":[{"type":"text","text":"hello"}]}
            ]}}
            """;

    private static final String HELLO_KOR_BODY = """
            {"content":{"type":"doc","content":[
              {"type":"paragraph","content":[{"type":"text","text":"안녕"}]}
            ]}}
            """;

    @Test
    @DisplayName("PUT - 응답은 id/materialId/updatedAt 메타데이터만 반환한다")
    void put_returns_metadata_only() throws Exception {
        mockMvc.perform(put("/api/planner/materials/{id}/note", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(HELLO_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(note.getId()))
                .andExpect(jsonPath("$.data.materialId").value(material.getId()))
                .andExpect(jsonPath("$.data.updatedAt").exists())
                .andExpect(jsonPath("$.data.content").doesNotExist());
    }

    @Test
    @DisplayName("PUT 후 GET - 저장된 content가 반환된다")
    void put_then_get_returns_updated_content() throws Exception {
        mockMvc.perform(put("/api/planner/materials/{id}/note", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(HELLO_KOR_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/planner/materials/{id}/note", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.type").value("doc"))
                .andExpect(jsonPath("$.data.content.content[0].type").value("paragraph"))
                .andExpect(jsonPath("$.data.content.content[0].content[0].text").value("안녕"));
    }

    @Test
    @DisplayName("PUT 멱등성 - 동일 content 두 번 저장해도 결과는 동일하다")
    void put_idempotent() throws Exception {
        String body = """
                {"content":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"x"}]}]}}
                """;
        mockMvc.perform(put("/api/planner/materials/{id}/note", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/planner/materials/{id}/note", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/planner/materials/{id}/note", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.content.content[0].content[0].text").value("x"));
    }

    @Test
    @DisplayName("PUT - 직렬화 1MB 초과 시 400 SP-ERR-002")
    void put_oversize_returns_400() throws Exception {
        String huge = "a".repeat(1024 * 1024 + 100);
        String body = "{\"content\":{\"type\":\"doc\",\"content\":[{\"type\":\"text\",\"text\":\""
                + huge + "\"}]}}";

        mockMvc.perform(put("/api/planner/materials/{id}/note", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SP-ERR-002"));
    }

    @Test
    @DisplayName("GET - 타인 자료의 노트 조회 시 404")
    void get_other_member_note_returns_404() throws Exception {
        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));
        noteRepository.save(new Note(otherMember.getId(), otherMaterial.getId()));

        mockMvc.perform(get("/api/planner/materials/{id}/note", otherMaterial.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));
    }

    @Test
    @DisplayName("PUT - 존재하지 않는 자료 ID는 404")
    void put_nonexistent_material_returns_404() throws Exception {
        mockMvc.perform(put("/api/planner/materials/{id}/note", 99999L)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":{"type":"doc","content":[]}}
                                """))
                .andExpect(status().isNotFound());
    }
}
