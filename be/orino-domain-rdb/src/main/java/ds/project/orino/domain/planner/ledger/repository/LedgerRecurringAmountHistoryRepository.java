package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerRecurringAmountHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LedgerRecurringAmountHistoryRepository
        extends JpaRepository<LedgerRecurringAmountHistory, Long> {

    List<LedgerRecurringAmountHistory> findAllByRecurringIdOrderByEffectiveFromAscIdAsc(
            Long recurringId);

    /** 점검 신호가 회원의 모든 항목 이력을 한 번에 훑는다. */
    List<LedgerRecurringAmountHistory> findAllByRecurringIdInOrderByEffectiveFromAscIdAsc(
            Collection<Long> recurringIds);
}
