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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final MemberRepository memberRepository;

    public CategoryService(CategoryRepository categoryRepository, MemberRepository memberRepository) {
        this.categoryRepository = categoryRepository;
        this.memberRepository = memberRepository;
    }

    public List<CategoryResponse> getCategories(Long memberId) {
        return categoryRepository.findByMemberIdOrderBySortOrder(memberId)
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse create(Long memberId, CreateCategoryRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        Category category = new Category(
                member,
                request.name(),
                request.color(),
                request.icon(),
                request.sortOrder()
        );

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long memberId, Long categoryId, UpdateCategoryRequest request) {
        Category category = categoryRepository.findByIdAndMemberId(categoryId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        category.update(request.name(), request.color(), request.icon(), request.sortOrder());

        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long memberId, Long categoryId) {
        Category category = categoryRepository.findByIdAndMemberId(categoryId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        categoryRepository.delete(category);
    }
}
