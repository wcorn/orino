package ds.project.orino.domain.planner.ledger.repository;

import ds.project.orino.domain.planner.ledger.entity.LedgerImportPreset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LedgerImportPresetRepository extends JpaRepository<LedgerImportPreset, Long> {

    /** 동봉 프리셋({@code memberId IS NULL})과 내가 만든 것을 함께 준다. */
    List<LedgerImportPreset> findAllByMemberIdIsNullOrMemberIdOrderByMemberIdAscNameAsc(
            Long memberId);

    Optional<LedgerImportPreset> findByIdAndMemberId(Long id, Long memberId);

    boolean existsByMemberIdIsNullAndName(String name);
}
