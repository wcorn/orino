package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 이 키들 중 <b>아직 누군가 가리키고 있는</b> 것. 보존 배치가 그 차집합을 고아로 본다.
     *
     * <p>키를 통째로 훑지 않고 후보를 받아 되묻는다 — 버킷에는 영수증 말고도 다른 prefix가
     * 있고, 첨부 행 전체를 메모리에 올릴 이유도 없다.
     */
    @Query("SELECT r.objectKey FROM LedgerTransactionReceipt r WHERE r.objectKey IN :objectKeys")
    List<String> findReferencedKeys(@Param("objectKeys") Collection<String> objectKeys);
}
