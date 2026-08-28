package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerSettingsRepository extends JpaRepository<LedgerSettings, Long> {
}
