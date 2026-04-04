package ds.project.orino.domain.material.repository;

import ds.project.orino.domain.material.entity.ReviewConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewConfigRepository
        extends JpaRepository<ReviewConfig, Long> {

    Optional<ReviewConfig> findByMaterialId(Long materialId);
}
