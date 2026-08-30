package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LedgerImportBatchRepository extends JpaRepository<LedgerImportBatch, Long> {

    /** 최근 것이 위로. 되돌릴 배치는 거의 언제나 방금 넣은 것이다. */
    List<LedgerImportBatch> findAllByMemberIdOrderByCreatedAtDescIdDesc(Long memberId);

    Optional<LedgerImportBatch> findByIdAndMemberId(Long id, Long memberId);
}
