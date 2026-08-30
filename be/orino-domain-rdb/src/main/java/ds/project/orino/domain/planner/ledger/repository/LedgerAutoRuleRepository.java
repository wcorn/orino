package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerAutoRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LedgerAutoRuleRepository extends JpaRepository<LedgerAutoRule, Long> {

    /**
     * 우선순위 순. <b>같은 순위가 둘이면 id로 다시 가른다</b> — 순서가 흔들리면 같은 내용에
     * 다른 카테고리가 붙고, 그건 재현되지 않아 버그로 잡기 어렵다.
     */
    List<LedgerAutoRule> findAllByMemberIdOrderByPriorityAscIdAsc(Long memberId);

    Optional<LedgerAutoRule> findByIdAndMemberId(Long id, Long memberId);

    /** 카테고리를 지울 때 그 카테고리를 가리키던 규칙이 남아 있는지 본다. */
    List<LedgerAutoRule> findAllByMemberIdAndCategoryId(Long memberId, Long categoryId);
}
