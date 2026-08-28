package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerInstallment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LedgerInstallmentRepository extends JpaRepository<LedgerInstallment, Long> {

    Optional<LedgerInstallment> findByIdAndMemberId(Long id, Long memberId);

    List<LedgerInstallment> findAllByMemberIdAndStatus(
            Long memberId, LedgerInstallment.Status status);

    Optional<LedgerInstallment> findByTransactionId(Long transactionId);
}
