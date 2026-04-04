package ds.project.orino.domain.material.repository;

import ds.project.orino.domain.material.entity.MaterialAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MaterialAllocationRepository
        extends JpaRepository<MaterialAllocation, Long> {

    Optional<MaterialAllocation> findByMaterialId(Long materialId);
}
