package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LedgerPointRepository extends JpaRepository<LedgerPoint, Long> {

    List<LedgerPoint> findAllByMemberIdOrderByDisplayOrderAscIdAsc(Long memberId);

    Optional<LedgerPoint> findByIdAndMemberId(Long id, Long memberId);
}
