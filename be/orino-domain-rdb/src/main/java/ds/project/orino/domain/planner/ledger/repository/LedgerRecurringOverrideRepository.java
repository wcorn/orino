package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerOverrideAction;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurringOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LedgerRecurringOverrideRepository
        extends JpaRepository<LedgerRecurringOverride, Long> {

    Optional<LedgerRecurringOverride> findByRecurringIdAndOccurrenceDate(
            Long recurringId, LocalDate occurrenceDate);

    List<LedgerRecurringOverride> findAllByRecurringIdOrderByOccurrenceDateDesc(Long recurringId);

    /** 여러 항목의 회차를 한 번에. 전개할 때마다 항목별로 물으면 질의가 항목 수만큼 늘어난다. */
    List<LedgerRecurringOverride> findAllByRecurringIdIn(Collection<Long> recurringIds);

    /** 미납 목록. 대시보드 상시 경고가 이걸 읽는다 — 확정하거나 건너뛰어야만 사라진다. */
    List<LedgerRecurringOverride> findAllByRecurringIdInAndAction(
            Collection<Long> recurringIds, LedgerOverrideAction action);
}
