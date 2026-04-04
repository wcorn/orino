package ds.project.orino.domain.preference.repository;

import ds.project.orino.domain.preference.entity.PriorityRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriorityRuleRepository
        extends JpaRepository<PriorityRule, Long> {

    List<PriorityRule> findByMemberIdOrderBySortOrder(Long memberId);

    Optional<PriorityRule> findByMemberIdAndItemType(
            Long memberId,
            ds.project.orino.domain.preference.entity.PriorityItemType itemType);
}
