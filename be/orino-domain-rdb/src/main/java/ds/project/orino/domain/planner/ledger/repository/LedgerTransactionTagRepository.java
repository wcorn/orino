package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionTag;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionTagId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LedgerTransactionTagRepository
        extends JpaRepository<LedgerTransactionTag, LedgerTransactionTagId> {

    List<LedgerTransactionTag> findAllByIdTransactionId(Long transactionId);

    /** 목록 배치 로딩: 여러 거래의 태그를 한 번에. */
    List<LedgerTransactionTag> findAllByIdTransactionIdIn(Collection<Long> transactionIds);

    void deleteByIdTransactionId(Long transactionId);
}
