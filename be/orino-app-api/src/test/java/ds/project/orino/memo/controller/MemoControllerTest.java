package ds.project.orino.memo.controller;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.memo.entity.Memo;
import ds.project.orino.domain.memo.repository.MemoRepository;
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

class MemoControllerTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemoRepository memoRepository;

    @Autowired
    private DbCleaner dbCleaner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member member;
    private Member otherMember;
    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        member = memberRepository.save(MemberFixture.create());
        otherMember = memberRepository.save(MemberFixture.create("other", "password"));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    private Memo root(String title, int sortOrder) {
        return memoRepository.save(new Memo(member.getId(), null, title, sortOrder));
    }

    private Memo child(Long parentId, String title, int sortOrder) {
        return memoRepository.save(new Memo(member.getId(), parentId, title, sortOrder));
    }

    @Test
    @DisplayName("GET memos - 메모가 없으면 빈 트리")
    void tree_empty() throws Exception {
        mockMvc.perform(get("/api/memos")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memos", hasSize(0)));
    }

    @Test
    @DisplayName("GET memos - 중첩 트리를 sortOrder 순으로 조립한다 (content 제외)")
    void tree_nested() throws Exception {
        Memo r1 = root("메모1", 0);
        Memo r2 = root("메모2", 1);
        Memo c1 = child(r1.getId(), "1-1", 0);
        child(c1.getId(), "1-1-1", 0);

        mockMvc.perform(get("/api/memos")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memos", hasSize(2)))
                .andExpect(jsonPath("$.data.memos[0].id").value(r1.getId()))
                .andExpect(jsonPath("$.data.memos[0].title").value("메모1"))
                .andExpect(jsonPath("$.data.memos[0].children", hasSize(1)))
                .andExpect(jsonPath("$.data.memos[0].children[0].id").value(c1.getId()))
                .andExpect(jsonPath("$.data.memos[0].children[0].children", hasSize(1)))
                .andExpect(jsonPath("$.data.memos[0].children[0].children[0].title").value("1-1-1"))
                .andExpect(jsonPath("$.data.memos[1].id").value(r2.getId()))
                .andExpect(jsonPath("$.data.memos[0].content").doesNotExist());
    }

    @Test
    @DisplayName("GET memos - 타인 메모는 트리에 포함되지 않는다")
    void tree_excludes_other_member() throws Exception {
        root("내 메모", 0);
        memoRepository.save(new Memo(otherMember.getId(), null, "남의 메모", 0));

        mockMvc.perform(get("/api/memos")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memos", hasSize(1)))
                .andExpect(jsonPath("$.data.memos[0].title").value("내 메모"));
    }

    @Test
    @DisplayName("POST memos - 루트 메모 생성 (parentId null, sortOrder=같은 부모 max+1)")
    void create_root() throws Exception {
        root("기존 루트", 0);

        mockMvc.perform(post("/api/memos")
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
    @DisplayName("POST memos - title 생략 시 기본 제목, 하위 메모 생성")
    void create_child_default_title() throws Exception {
        Memo parent = root("부모", 0);

        mockMvc.perform(post("/api/memos")
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
    @DisplayName("POST memos - 타인 메모를 부모로 지정하면 404")
    void create_with_others_parent_404() throws Exception {
        Memo othersMemo = memoRepository.save(
                new Memo(otherMember.getId(), null, "남의 메모", 0));

        mockMvc.perform(post("/api/memos")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId": %d}
                                """.formatted(othersMemo.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));
    }

    @Test
    @DisplayName("GET memo/{id} - 단건 content 포함 조회")
    void detail_with_content() throws Exception {
        Memo memo = root("메모", 0);

        mockMvc.perform(get("/api/memos/{id}", memo.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(memo.getId()))
                .andExpect(jsonPath("$.data.title").value("메모"))
                .andExpect(jsonPath("$.data.content.type").value("doc"));
    }

    @Test
    @DisplayName("GET memo/{id} - 타인 메모 조회 시 404")
    void detail_other_member_404() throws Exception {
        Memo othersMemo = memoRepository.save(
                new Memo(otherMember.getId(), null, "남의 메모", 0));

        mockMvc.perform(get("/api/memos/{id}", othersMemo.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SP-ERR-001"));
    }

    @Test
    @DisplayName("PATCH memo - title/content 부분 수정")
    void update_title_content() throws Exception {
        Memo memo = root("원래 제목", 0);

        mockMvc.perform(patch("/api/memos/{id}", memo.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "바뀐 제목",
                                 "content": {"type":"doc","content":[
                                   {"type":"paragraph","content":[{"type":"text","text":"내용"}]}]}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("바뀐 제목"));

        mockMvc.perform(get("/api/memos/{id}", memo.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(jsonPath("$.data.content.content[0].content[0].text").value("내용"));
    }

    @Test
    @DisplayName("PATCH memo - 본문이 모두 null이면 400")
    void update_empty_400() throws Exception {
        Memo memo = root("메모", 0);

        mockMvc.perform(patch("/api/memos/{id}", memo.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH memo - parentId 이동 (정상)")
    void update_move_parent() throws Exception {
        Memo a = root("A", 0);
        Memo b = root("B", 1);

        mockMvc.perform(patch("/api/memos/{id}", b.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId": %d}
                                """.formatted(a.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentId").value(a.getId()));
    }

    @Test
    @DisplayName("PATCH memo - 자기 자신을 부모로 지정하면 400")
    void update_self_parent_400() throws Exception {
        Memo memo = root("메모", 0);

        mockMvc.perform(patch("/api/memos/{id}", memo.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId": %d}
                                """.formatted(memo.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SP-ERR-002"));
    }

    @Test
    @DisplayName("PATCH memo - 자손을 부모로 지정하면 사이클이라 400")
    void update_descendant_parent_cycle_400() throws Exception {
        Memo a = root("A", 0);
        Memo b = child(a.getId(), "B", 0);
        Memo c = child(b.getId(), "C", 0);

        // A를 C(자손)의 자식으로 이동 → 사이클
        mockMvc.perform(patch("/api/memos/{id}", a.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId": %d}
                                """.formatted(c.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SP-ERR-002"));
    }

    @Test
    @DisplayName("DELETE memo - 서브트리가 cascade 삭제된다")
    void delete_subtree_cascade() throws Exception {
        Memo a = root("A", 0);
        Memo b = child(a.getId(), "B", 0);
        Memo c = child(b.getId(), "C", 0);
        Memo sibling = root("형제", 1);

        mockMvc.perform(delete("/api/memos/{id}", a.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNoContent());

        assertThat(memoCount(a.getId())).isZero();
        assertThat(memoCount(b.getId())).isZero();
        assertThat(memoCount(c.getId())).isZero();
        assertThat(memoCount(sibling.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("DELETE memo - 타인 메모 삭제 시 404")
    void delete_other_member_404() throws Exception {
        Memo othersMemo = memoRepository.save(
                new Memo(otherMember.getId(), null, "남의 메모", 0));

        mockMvc.perform(delete("/api/memos/{id}", othersMemo.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isNotFound());
    }

    private Integer memoCount(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memo WHERE id = ?", Integer.class, id);
    }
}
