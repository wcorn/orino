package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LedgerAssetRepository extends JpaRepository<LedgerAsset, Long> {

    List<LedgerAsset> findAllByMemberIdOrderByDisplayOrderAscIdAsc(Long memberId);

    Optional<LedgerAsset> findByIdAndMemberId(Long id, Long memberId);

    /** 그룹을 지울 때 그 그룹을 쓰는 자산이 남아 있는지 본다. */
    List<LedgerAsset> findAllByMemberIdAndGroupId(Long memberId, Long groupId);

    /** 이 계좌를 연결 계좌로 삼은 체크카드들. 계좌를 숨길 때 함께 알려 준다. */
    List<LedgerAsset> findAllByMemberIdAndLinkedAssetId(Long memberId, Long linkedAssetId);

    /** 삭제 전 확인용 — 이 자산을 물고 있는 체크카드가 있으면 지울 수 없다. */
    boolean existsByMemberIdAndLinkedAssetId(Long memberId, Long linkedAssetId);

    /** 삭제 전 확인용 — 이 계좌를 결제 계좌로 삼은 신용카드가 있으면 지울 수 없다. */
    boolean existsByMemberIdAndPaymentAssetId(Long memberId, Long paymentAssetId);
}
