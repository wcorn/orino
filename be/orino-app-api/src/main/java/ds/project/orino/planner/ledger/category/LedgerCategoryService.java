package ds.project.orino.planner.ledger.category;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.ledger.entity.LedgerCategory;
import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.repository.LedgerCategoryRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.planner.ledger.category.dto.CategoryDtos;
import ds.project.orino.planner.ledger.common.LedgerBootstrap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 카테고리 읽기·쓰기.
 *
 * <p>두 규칙이 이 클래스의 전부다.
 * <ul>
 *   <li><b>2단까지만.</b> 깊이를 열어 두면 「식비 &gt; 외식 &gt; 점심 &gt; 회사 근처」까지
 *       파고들어 결국 아무도 분류하지 않게 된다</li>
 *   <li><b>지우지 않고 보관한다.</b> 삭제도 통합도 거래를 건드리지 않는다 — 통합은 소속을
 *       옮기고, 삭제는 원본을 보관 처리할 뿐이다. 카테고리가 사라지면 과거 통계에서
 *       그 이름이 사라진다</li>
 * </ul>
 */
@Service
public class LedgerCategoryService {

    private final LedgerCategoryRepository categoryRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerBootstrap bootstrap;

    public LedgerCategoryService(LedgerCategoryRepository categoryRepository,
                                 LedgerTransactionRepository transactionRepository,
                                 LedgerBootstrap bootstrap) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.bootstrap = bootstrap;
    }

    /** 최초 진입이면 여기서 프리셋 13종이 심긴다(D-14). */
    @Transactional
    public List<CategoryDtos.View> list(Long memberId, LedgerFlow flow) {
        bootstrap.ensureSeeded(memberId);

        List<LedgerCategory> all = flow != null
                ? categoryRepository.findAllByMemberIdAndFlowOrderByDisplayOrderAscIdAsc(memberId, flow)
                : categoryRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId);

        Map<Long, List<CategoryDtos.View>> childrenByParent = new HashMap<>();
        for (LedgerCategory category : all) {
            if (!category.isRoot()) {
                childrenByParent.computeIfAbsent(category.getParentId(), key -> new ArrayList<>())
                        .add(CategoryDtos.View.of(category, List.of()));
            }
        }
        List<CategoryDtos.View> roots = new ArrayList<>();
        for (LedgerCategory category : all) {
            if (category.isRoot()) {
                roots.add(CategoryDtos.View.of(category,
                        childrenByParent.getOrDefault(category.getId(), List.of())));
            }
        }
        return roots;
    }

    @Transactional
    public CategoryDtos.View create(Long memberId, CategoryDtos.Create request) {
        bootstrap.ensureSeeded(memberId);

        if (request.parentId() != null) {
            LedgerCategory parent = requireCategory(memberId, request.parentId());
            requireRoot(parent);
            requireSameFlow(parent, request.flow());
        }
        LedgerCategory category = new LedgerCategory(memberId, request.flow(), request.name(),
                request.parentId(), request.displayOrder() != null ? request.displayOrder() : 0);
        category.updateColor(request.color());
        category.updateIcon(request.icon());
        categoryRepository.save(category);
        return CategoryDtos.View.of(category, List.of());
    }

    @Transactional
    public CategoryDtos.View update(Long memberId, Long id, CategoryDtos.Update request) {
        LedgerCategory category = requireCategory(memberId, id);

        if (Boolean.TRUE.equals(request.clearParent())) {
            category.updateParentId(null);
        } else if (request.parentId() != null) {
            if (request.parentId().equals(id)) {
                throw new CustomException(ErrorCode.LEDGER_CATEGORY_CYCLE);
            }
            LedgerCategory parent = requireCategory(memberId, request.parentId());
            // 2단이라 「내 하위로 옮기기」는 곧 「내 자식을 부모로 삼기」다.
            if (id.equals(parent.getParentId())) {
                throw new CustomException(ErrorCode.LEDGER_CATEGORY_CYCLE);
            }
            requireRoot(parent);
            requireSameFlow(parent, category.getFlow());
            // 하위를 가진 대분류는 하위 분류가 될 수 없다 — 그 순간 3단이 된다.
            if (!categoryRepository.findAllByMemberIdAndParentId(memberId, id).isEmpty()) {
                throw new CustomException(ErrorCode.LEDGER_CATEGORY_TOO_DEEP);
            }
            category.updateParentId(request.parentId());
        }
        if (request.name() != null) {
            // 이름만 바꾸면 내역은 그대로 따라온다 — 소속을 옮기는 게 아니기 때문이다.
            category.updateName(request.name());
        }
        if (request.color() != null) {
            category.updateColor(request.color());
        }
        if (request.icon() != null) {
            category.updateIcon(request.icon());
        }
        // 속성 셋은 함께 바꾼다 — 화면에서도 한 줄로 다루는 값들이다(v2).
        if (request.costType() != null || Boolean.TRUE.equals(request.clearCostType())
                || request.excludeFromCardGoal() != null
                || request.excludeFromSettlement() != null) {
            category.updateAttributes(
                    Boolean.TRUE.equals(request.clearCostType())
                            ? null
                            : (request.costType() == null
                                    ? category.getCostType() : request.costType()),
                    request.excludeFromCardGoal() == null
                            ? category.isExcludeFromCardGoal() : request.excludeFromCardGoal(),
                    request.excludeFromSettlement() == null
                            ? category.isExcludeFromSettlement()
                            : request.excludeFromSettlement());
        }
        if (request.displayOrder() != null) {
            category.updateDisplayOrder(request.displayOrder());
        }
        return CategoryDtos.View.of(category, List.of());
    }

    /**
     * 삭제는 <b>보관</b>이다. 행을 지우면 그 카테고리로 잡힌 과거 지출의 이름이 사라진다.
     *
     * <p>붙어 있던 거래는 그대로 둔다 — 미분류로 되돌리지 않는다. 사용자가 옮기고 싶으면
     * 통합({@link #merge})이 그 일을 한다.
     */
    @Transactional
    public void archive(Long memberId, Long id) {
        LedgerCategory category = requireCategory(memberId, id);
        category.archive();
        // 하위 분류도 함께 보관한다. 부모가 사라진 하위만 남으면 목록에서 갈 곳이 없다.
        categoryRepository.findAllByMemberIdAndParentId(memberId, id)
                .forEach(LedgerCategory::archive);
    }

    /** 통합 — <b>내역이 따라온다</b>. 원본은 보관되고 거래는 하나도 지워지지 않는다. */
    @Transactional
    public CategoryDtos.MergeResponse merge(Long memberId, Long id, CategoryDtos.MergeRequest request) {
        LedgerCategory source = requireCategory(memberId, id);
        LedgerCategory target = requireCategory(memberId, request.targetCategoryId());
        if (source.getId().equals(target.getId())) {
            throw new CustomException(ErrorCode.LEDGER_CATEGORY_CYCLE);
        }
        if (source.getFlow() != target.getFlow()) {
            // 지출 카테고리를 수입 카테고리로 합치면 그 거래들의 유형이 카테고리와 어긋난다.
            throw new CustomException(ErrorCode.LEDGER_CATEGORY_FLOW_MISMATCH);
        }
        int moved = transactionRepository.moveCategory(memberId, source.getId(), target.getId());
        source.archive();
        return new CategoryDtos.MergeResponse(moved);
    }

    private LedgerCategory requireCategory(Long memberId, Long id) {
        return categoryRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_CATEGORY_NOT_FOUND));
    }

    private void requireRoot(LedgerCategory parent) {
        if (!parent.isRoot()) {
            throw new CustomException(ErrorCode.LEDGER_CATEGORY_TOO_DEEP);
        }
    }

    private void requireSameFlow(LedgerCategory parent, LedgerFlow flow) {
        if (parent.getFlow() != flow) {
            throw new CustomException(ErrorCode.LEDGER_CATEGORY_FLOW_MISMATCH);
        }
    }
}
