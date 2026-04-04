package ds.project.orino.domain.material.repository;

import ds.project.orino.domain.material.entity.MaterialDailyOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MaterialDailyOverrideRepository
        extends JpaRepository<MaterialDailyOverride, Long> {

    List<MaterialDailyOverride> findByMaterialIdOrderByOverrideDate(
            Long materialId);

    Optional<MaterialDailyOverride> findByMaterialIdAndOverrideDate(
            Long materialId, LocalDate overrideDate);
}
