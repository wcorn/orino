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
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                new StudyMaterial(member.getId(), "이펙티브 자바", MaterialType.BOOK));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    private Note root(String title, int sortOrder) {
        return noteRepository.save(new Note(member.getId(), material.getId(), null, title, sortOrder));
    }

    private Note child(Long parentId, String title, int sortOrder) {
        return noteRepository.save(new Note(member.getId(), material.getId(), parentId, title, sortOrder));
    }

    @Test
    @DisplayName("GET notes - 노트가 없으면 빈 트리")
    void tree_empty() throws Exception {
        mockMvc.perform(get("/api/planner/materials/{id}/notes", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notes", hasSize(0)));
    }

    @Test
    @DisplayName("GET notes - 중첩 트리를 sortOrder 순으로 조립한다 (content 제외)")
    void tree_nested() throws Exception {
        Note r1 = root("1장", 0);
        Note r2 = root("2장", 1);
        Note c1 = child(r1.getId(), "1-1 절", 0);
        child(c1.getId(), "1-1-1 항", 0);

        mockMvc.perform(get("/api/planner/materials/{id}/notes", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notes", hasSize(2)))
                .andExpect(jsonPath("$.data.notes[0].id").value(r1.getId()))
                .andExpect(jsonPath("$.data.notes[0].title").value("1장"))
                .andExpect(jsonPath("$.data.notes[0].children", hasSize(1)))
                .andExpect(jsonPath("$.data.notes[0].children[0].id").value(c1.getId()))
                .andExpect(jsonPath("$.data.notes[0].children[0].children", hasSize(1)))
                .andExpect(jsonPath("$.data.notes[0].children[0].children[0].title").value("1-1-1 항"))
                .andExpect(jsonPath("$.data.notes[1].id").value(r2.getId()))
                .andExpect(jsonPath("$.data.notes[0].content").doesNotExist());
    }

    @Test
    @DisplayName("GET notes - 타인 자료의 트리 조회 시 404")
    void tree_other_material_404() throws Exception {
        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));

        mockMvc.perform(get("/api/planner/materials/{id}/notes", otherMaterial.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST notes - 루트 노트 생성 (parentId null, sortOrder=같은 부모 max+1)")
    void create_root() throws Exception {
        root("기존 루트", 0);

        mockMvc.perform(post("/api/planner/materials/{id}/notes", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "새 루트"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("새 루트"))
                .andExpect(jsonPath("$.data.parentId").doesNotExist())
                .andExpect(jsonPath("$.data.sortOrder").value(1))
                .andExpect(jsonPath("$.data.content.type").value("doc"))
                .andExpect(jsonPath("$.data.content.content").isArray());
    }

    @Test
    @DisplayName("POST notes - title 생략 시 기본 제목, 하위 노트 생성")
    void create_child_default_title() throws Exception {
        Note parent = root("부모", 0);

        mockMvc.perform(post("/api/planner/materials/{id}/notes", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId": %d}
                                """.formatted(parent.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parentId").value(parent.getId()))
                .andExpect(jsonPath("$.data.title").value("제목 없음"))
                .andExpect(jsonPath("$.data.sortOrder").value(0));
    }

    @Test
    @DisplayName("POST notes - 다른 자료의 노트를 부모로 지정하면 400")
    void create_with_cross_material_parent_400() throws Exception {
        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "다른 자료", MaterialType.BOOK));
        Note otherNote = noteRepository.save(
                new Note(member.getId(), otherMaterial.getId(), null, "다른 자료 노트", 0));

        mockMvc.perform(post("/api/planner/materials/{id}/notes", material.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId": %d}
                                """.formatted(otherNote.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SP-ERR-002"));
    }

    @Test
    @DisplayName("GET note/{id} - 단건 content 포함 조회")
    void detail_with_content() throws Exception {
        Note note = root("노트", 0);

        mockMvc.perform(get("/api/planner/notes/{id}", note.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(note.getId()))
                .andExpect(jsonPath("$.data.materialId").value(material.getId()))
                .andExpect(jsonPath("$.data.title").value("노트"))
                .andExpect(jsonPath("$.data.content.type").value("doc"));
    }

    @Test
    @DisplayName("GET note/{id} - 타인 노트 조회 시 404")
    void detail_other_member_404() throws Exception {
        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));
        Note otherNote = noteRepository.save(
                new Note(otherMember.getId(), otherMaterial.getId(), null, "남의 노트", 0));

        mockMvc.perform(get("/api/planner/notes/{id}", otherNote.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));
    }

    @Test
    @DisplayName("PATCH note - title/content 부분 수정")
    void update_title_content() throws Exception {
        Note note = root("원래 제목", 0);

        mockMvc.perform(patch("/api/planner/notes/{id}", note.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "바뀐 제목",
                                 "content": {"type":"doc","content":[
                                   {"type":"paragraph","content":[{"type":"text","text":"내용"}]}]}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("바뀐 제목"));

        mockMvc.perform(get("/api/planner/notes/{id}", note.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.content.content[0].content[0].text").value("내용"));
    }

    @Test
    @DisplayName("PATCH note - 본문이 모두 null이면 400")
    void update_empty_400() throws Exception {
        Note note = root("노트", 0);

        mockMvc.perform(patch("/api/planner/notes/{id}", note.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH note - parentId 이동 (정상)")
    void update_move_parent() throws Exception {
        Note a = root("A", 0);
        Note b = root("B", 1);

        mockMvc.perform(patch("/api/planner/notes/{id}", b.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId": %d}
                                """.formatted(a.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentId").value(a.getId()));
    }

    @Test
    @DisplayName("PATCH note - 자기 자신을 부모로 지정하면 400")
    void update_self_parent_400() throws Exception {
        Note note = root("노트", 0);

        mockMvc.perform(patch("/api/planner/notes/{id}", note.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId": %d}
                                """.formatted(note.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SP-ERR-002"));
    }

    @Test
    @DisplayName("PATCH note - 자손을 부모로 지정하면 사이클이라 400")
    void update_descendant_parent_cycle_400() throws Exception {
        Note a = root("A", 0);
        Note b = child(a.getId(), "B", 0);
        Note c = child(b.getId(), "C", 0);

        // A를 C(자손)의 자식으로 이동 → 사이클
        mockMvc.perform(patch("/api/planner/notes/{id}", a.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId": %d}
                                """.formatted(c.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SP-ERR-002"));
    }

    @Test
    @DisplayName("DELETE note - 서브트리가 cascade 삭제된다")
    void delete_subtree_cascade() throws Exception {
        Note a = root("A", 0);
        Note b = child(a.getId(), "B", 0);
        Note c = child(b.getId(), "C", 0);
        Note sibling = root("형제", 1);

        mockMvc.perform(delete("/api/planner/notes/{id}", a.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNoContent());

        assertThat(noteCount(a.getId())).isZero();
        assertThat(noteCount(b.getId())).isZero();
        assertThat(noteCount(c.getId())).isZero();
        assertThat(noteCount(sibling.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("DELETE note - 타인 노트 삭제 시 404")
    void delete_other_member_404() throws Exception {
        StudyMaterial otherMaterial = studyMaterialRepository.save(
                new StudyMaterial(otherMember.getId(), "남의 자료", MaterialType.BOOK));
        Note otherNote = noteRepository.save(
                new Note(otherMember.getId(), otherMaterial.getId(), null, "남의 노트", 0));

        mockMvc.perform(delete("/api/planner/notes/{id}", otherNote.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound());
    }

    private Integer noteCount(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM note WHERE id = ?", Integer.class, id);
    }
}
