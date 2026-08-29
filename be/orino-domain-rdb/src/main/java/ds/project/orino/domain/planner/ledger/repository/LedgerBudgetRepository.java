package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LedgerBudgetRepository extends JpaRepository<LedgerBudget, Long> {

    Optional<LedgerBudget> findByMemberIdAndPeriod(Long memberId, String period);
}
