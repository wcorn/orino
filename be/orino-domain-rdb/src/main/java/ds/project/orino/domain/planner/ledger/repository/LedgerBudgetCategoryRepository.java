package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerBudgetCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerBudgetCategoryRepository
        extends JpaRepository<LedgerBudgetCategory, Long> {

    List<LedgerBudgetCategory> findAllByBudgetId(Long budgetId);

    void deleteAllByBudgetId(Long budgetId);
}
