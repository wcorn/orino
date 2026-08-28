package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LedgerTransactionTemplateRepository
        extends JpaRepository<LedgerTransactionTemplate, Long> {

    /**
     * 많이 쓴 순. 같은 횟수면 최근에 만든 것이 위로 온다 —
     * 새로 만든 템플릿이 목록 끝에 묻히면 쓸 일이 없다.
     */
    List<LedgerTransactionTemplate> findAllByMemberIdOrderByUseCountDescIdDesc(Long memberId);

    Optional<LedgerTransactionTemplate> findByIdAndMemberId(Long id, Long memberId);
}
