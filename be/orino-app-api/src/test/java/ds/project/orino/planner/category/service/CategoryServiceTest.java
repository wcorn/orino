package ds.project.orino.planner.category.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.category.entity.Category;
import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.planner.category.dto.CategoryResponse;
import ds.project.orino.planner.category.dto.CreateCategoryRequest;
import ds.project.orino.planner.category.dto.UpdateCategoryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private CategoryService categoryService;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, memberRepository);
    }

    @Test
    @DisplayName("카테고리 목록을 정렬 순서대로 조회한다")
    void getCategories() {
        Member member = new Member("admin", "encoded");
        Category category = new Category(member, "프로그래밍", "#FF9800", "code", 0);

        given(categoryRepository.findByMemberIdOrderBySortOrder(1L))
                .willReturn(List.of(category));

        List<CategoryResponse> result = categoryService.getCategories(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("프로그래밍");
    }

    @Test
    @DisplayName("카테고리를 생성한다")
    void create() {
        Member member = new Member("admin", "encoded");
        Category saved = new Category(member, "알고리즘", "#9C27B0", "puzzle", 1);

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(categoryRepository.save(any(Category.class))).willReturn(saved);

        CategoryResponse result = categoryService.create(1L,
                new CreateCategoryRequest("알고리즘", "#9C27B0", "puzzle", 1));

        assertThat(result.name()).isEqualTo("알고리즘");
        assertThat(result.color()).isEqualTo("#9C27B0");
    }

    @Test
    @DisplayName("존재하지 않는 회원으로 카테고리 생성 시 예외를 던진다")
    void create_memberNotFound() {
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.create(999L,
                new CreateCategoryRequest("테스트", "#000000", null, 0)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("카테고리를 수정한다")
    void update() {
        Member member = new Member("admin", "encoded");
        Category category = new Category(member, "기존이름", "#000000", null, 0);

        given(categoryRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(category));

        CategoryResponse result = categoryService.update(1L, 1L,
                new UpdateCategoryRequest("새이름", "#FF0000", "star", 2));

        assertThat(result.name()).isEqualTo("새이름");
        assertThat(result.color()).isEqualTo("#FF0000");
        assertThat(result.sortOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("존재하지 않는 카테고리 수정 시 예외를 던진다")
    void update_notFound() {
        given(categoryRepository.findByIdAndMemberId(999L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(1L, 999L,
                new UpdateCategoryRequest("이름", "#000000", null, 0)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("카테고리를 삭제한다")
    void delete() {
        Member member = new Member("admin", "encoded");
        Category category = new Category(member, "삭제대상", "#000000", null, 0);

        given(categoryRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(category));

        categoryService.delete(1L, 1L);

        verify(categoryRepository).delete(category);
    }

    @Test
    @DisplayName("존재하지 않는 카테고리 삭제 시 예외를 던진다")
    void delete_notFound() {
        given(categoryRepository.findByIdAndMemberId(999L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.delete(1L, 999L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
