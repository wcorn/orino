package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LedgerTransactionReceiptRepository
        extends JpaRepository<LedgerTransactionReceipt, Long> {

    List<LedgerTransactionReceipt> findAllByMemberIdAndTransactionIdOrderByDisplayOrderAscIdAsc(
            Long memberId, Long transactionId);

    /** 목록 배치 로딩: 여러 거래의 첨부를 한 번에. */
    List<LedgerTransactionReceipt> findAllByMemberIdAndTransactionIdIn(
            Long memberId, Collection<Long> transactionIds);

    Optional<LedgerTransactionReceipt> findByIdAndMemberId(Long id, Long memberId);
}
