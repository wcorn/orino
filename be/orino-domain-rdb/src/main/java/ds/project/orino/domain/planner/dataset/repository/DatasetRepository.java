package ds.project.orino.domain.planner.dataset.repository;

import ds.project.orino.domain.planner.dataset.entity.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {

    Optional<Dataset> findByIdAndMemberId(Long id, Long memberId);
}
