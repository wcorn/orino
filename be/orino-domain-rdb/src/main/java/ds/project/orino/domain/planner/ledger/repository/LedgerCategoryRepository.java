package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerCategory;
import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LedgerCategoryRepository extends JpaRepository<LedgerCategory, Long> {

    List<LedgerCategory> findAllByMemberIdOrderByDisplayOrderAscIdAsc(Long memberId);

    List<LedgerCategory> findAllByMemberIdAndFlowOrderByDisplayOrderAscIdAsc(Long memberId, LedgerFlow flow);

    Optional<LedgerCategory> findByIdAndMemberId(Long id, Long memberId);

    /** 프리셋을 이미 심었는지. 회원 생성 훅이 아니라 최초 진입 때 심기 때문에 필요하다(D-14). */
    boolean existsByMemberId(Long memberId);

    /** 하위 분류. 대분류를 지울 때와 3단 여부를 볼 때 함께 쓴다. */
    List<LedgerCategory> findAllByMemberIdAndParentId(Long memberId, Long parentId);
}
