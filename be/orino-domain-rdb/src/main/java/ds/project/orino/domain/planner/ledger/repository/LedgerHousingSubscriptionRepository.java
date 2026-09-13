package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerHousingSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 청약홈 기준값. 회원 소유 확인은 자산 쪽에서 먼저 한다 — 이 표는 {@code asset_id}만 안다.
 */
public interface LedgerHousingSubscriptionRepository
        extends JpaRepository<LedgerHousingSubscription, Long> {
}
