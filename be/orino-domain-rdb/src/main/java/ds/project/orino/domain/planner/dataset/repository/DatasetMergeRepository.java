package ds.project.orino.domain.planner.dataset.repository;

import ds.project.orino.domain.planner.dataset.entity.DatasetMerge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DatasetMergeRepository extends JpaRepository<DatasetMerge, Long> {

    /** 앵커 하나의 병합. 한 앵커에 병합은 하나(UNIQUE). */
    Optional<DatasetMerge> findByAnchorRowIdAndAnchorColKey(Long anchorRowId, String anchorColKey);

    /** 페이지 조회 시 그 행들의 병합을 한 번에. */
    List<DatasetMerge> findByAnchorRowIdIn(List<Long> anchorRowIds);

    /** 열 삭제·순서 변경 시 정리를 위해 그 dataset의 병합을 전부. sparse라 대개 적다. */
    List<DatasetMerge> findByDatasetId(Long datasetId);
}
