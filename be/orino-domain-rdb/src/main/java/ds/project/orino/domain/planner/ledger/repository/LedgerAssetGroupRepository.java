package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerAssetGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LedgerAssetGroupRepository extends JpaRepository<LedgerAssetGroup, Long> {

    List<LedgerAssetGroup> findAllByMemberIdOrderByDisplayOrderAscIdAsc(Long memberId);

    /** 남의 그룹을 건드릴 수 없게 조회 단계에서 회원을 함께 건다. */
    Optional<LedgerAssetGroup> findByIdAndMemberId(Long id, Long memberId);
}
