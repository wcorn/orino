package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LedgerTagRepository extends JpaRepository<LedgerTag, Long> {

    List<LedgerTag> findAllByMemberIdOrderByNameAsc(Long memberId);

    /** 입력할 때 이름으로 찾아 붙이고, 없으면 만든다. */
    List<LedgerTag> findAllByMemberIdAndNameIn(Long memberId, Collection<String> names);

    List<LedgerTag> findAllByMemberIdAndIdIn(Long memberId, Collection<Long> ids);
}
