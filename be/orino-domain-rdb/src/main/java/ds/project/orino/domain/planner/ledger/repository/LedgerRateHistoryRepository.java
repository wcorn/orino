package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerRateHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 금리 이력. 회원 소유 확인은 자산 쪽에서 먼저 한다 — 이 표는 {@code asset_id}만 안다. */
public interface LedgerRateHistoryRepository extends JpaRepository<LedgerRateHistory, Long> {

    /** 최신이 위다. 화면의 금리 이력 카드가 그 순서로 그린다. */
    List<LedgerRateHistory> findAllByAssetIdOrderByEffectiveFromDesc(Long assetId);

    Optional<LedgerRateHistory> findByAssetIdAndEffectiveFrom(Long assetId, LocalDate effectiveFrom);

    void deleteAllByAssetId(Long assetId);
}
