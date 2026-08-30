package ds.project.orino.planner.ledger.rule;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.ledger.entity.LedgerAutoRule;
import ds.project.orino.domain.planner.ledger.entity.LedgerCategory;
import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.repository.LedgerAutoRuleRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerCategoryRepository;
import ds.project.orino.planner.ledger.rule.dto.AutoRuleDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 자동 분류 규칙(`LDG-062`).
 *
 * <p><b>가져오기와 수동 입력이 같은 규칙을 지나간다.</b> 그래서 규칙 해석이 여기 한 곳에만
 * 있다 — 가져오기 쪽에만 두면 손으로 적은 거래는 분류되지 않고, 양쪽에 각각 두면 언젠가
 * 한쪽만 고쳐지는 날이 온다.
 *
 * <p><b>사람이 고른 카테고리를 덮지 않는다.</b> 규칙은 비어 있는 칸만 채운다 — 덮어쓰면
 * 「분명 바꿨는데 되돌아간다」가 되고, 그 순간 자동 분류를 끄게 된다.
 */
@Service
public class LedgerAutoRuleService {

    private final LedgerAutoRuleRepository ruleRepository;
    private final LedgerCategoryRepository categoryRepository;

    public LedgerAutoRuleService(LedgerAutoRuleRepository ruleRepository,
                                 LedgerCategoryRepository categoryRepository) {
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<AutoRuleDtos.View> list(Long memberId) {
        return ruleRepository.findAllByMemberIdOrderByPriorityAscIdAsc(memberId).stream()
                .map(rule -> view(memberId, rule))
                .toList();
    }

    @Transactional
    public AutoRuleDtos.View create(Long memberId, AutoRuleDtos.CreateRequest request) {
        requireExpenseCategory(memberId, request.categoryId());
        LedgerAutoRule rule = ruleRepository.save(new LedgerAutoRule(
                memberId, request.keyword().trim(), request.matchType(),
                request.categoryId(),
                request.priority() == null ? nextPriority(memberId) : request.priority()));
        return view(memberId, rule);
    }

    @Transactional
    public AutoRuleDtos.View update(Long memberId, Long id, AutoRuleDtos.UpdateRequest request) {
        LedgerAutoRule rule = ruleRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_AUTO_RULE_NOT_FOUND));
        if (request.categoryId() != null) {
            requireExpenseCategory(memberId, request.categoryId());
        }
        rule.update(request.keyword(), request.matchType(), request.categoryId(),
                request.priority(), request.enabled());
        return view(memberId, rule);
    }

    @Transactional
    public void delete(Long memberId, Long id) {
        LedgerAutoRule rule = ruleRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_AUTO_RULE_NOT_FOUND));
        ruleRepository.delete(rule);
    }

    /**
     * 규칙을 한 줄에 적용한다.
     *
     * <p>이미 카테고리가 정해져 있으면 <b>그대로 둔다.</b> 맞는 규칙이 없어도 그대로 둔다 —
     * 미분류는 실패가 아니라 「아직 안 정했다」이고, 원장은 미분류를 허용한다(확정 명세 §4.2).
     *
     * @return 규칙이 정해 준 카테고리, 또는 원래 값
     */
    public Long classify(List<LedgerAutoRule> rules, String title, Long categoryId) {
        if (categoryId != null || title == null || title.isBlank()) {
            return categoryId;
        }
        // 우선순위 순으로 보고 처음 맞는 하나만 쓴다. 둘을 합치면 결과가 순서에 달린다.
        return rules.stream()
                .filter(rule -> rule.matches(title))
                .map(LedgerAutoRule::getCategoryId)
                .findFirst()
                .orElse(null);
    }

    /** 여러 줄을 분류하기 전에 규칙을 한 번만 읽는다 — 줄마다 읽으면 질의가 줄 수만큼 는다. */
    @Transactional(readOnly = true)
    public List<LedgerAutoRule> rulesOf(Long memberId) {
        return ruleRepository.findAllByMemberIdOrderByPriorityAscIdAsc(memberId);
    }

    private int nextPriority(Long memberId) {
        return ruleRepository.findAllByMemberIdOrderByPriorityAscIdAsc(memberId).stream()
                .mapToInt(LedgerAutoRule::getPriority)
                .max()
                .orElse(0) + 1;
    }

    /**
     * 지출 카테고리만 규칙의 대상이다.
     *
     * <p>수입·이체에 규칙을 걸면 이체 한 건이 지출 카테고리를 갖게 되고, 그러면 통계에서
     * 같은 돈이 두 번 세어진다.
     */
    private void requireExpenseCategory(Long memberId, Long categoryId) {
        LedgerCategory category = categoryRepository.findByIdAndMemberId(categoryId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_CATEGORY_NOT_FOUND));
        if (category.getFlow() != LedgerFlow.EXPENSE) {
            throw new CustomException(ErrorCode.LEDGER_AUTO_RULE_CATEGORY_NOT_EXPENSE);
        }
    }

    private AutoRuleDtos.View view(Long memberId, LedgerAutoRule rule) {
        String name = categoryRepository.findByIdAndMemberId(rule.getCategoryId(), memberId)
                .map(LedgerCategory::getName)
                .orElse(null);
        return new AutoRuleDtos.View(rule.getId(), rule.getKeyword(), rule.getMatchType(),
                rule.getCategoryId(), name, rule.getPriority(), rule.isEnabled());
    }
}
