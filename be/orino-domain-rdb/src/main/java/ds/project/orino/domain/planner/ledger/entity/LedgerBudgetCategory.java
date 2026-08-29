package ds.project.orino.domain.planner.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 카테고리별 한도. 달마다 따로 정한다 — 8월 여행 예산이 9월까지 따라오면 안 된다. */
@Entity
@Table(name = "ledger_budget_category")
public class LedgerBudgetCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "budget_id", nullable = false)
    private Long budgetId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private long amount;

    protected LedgerBudgetCategory() {
    }

    public LedgerBudgetCategory(Long budgetId, Long categoryId, long amount) {
        this.budgetId = budgetId;
        this.categoryId = categoryId;
        this.amount = amount;
    }

    public void updateAmount(long amount) {
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Long getBudgetId() {
        return budgetId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public long getAmount() {
        return amount;
    }
}
