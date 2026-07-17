package ds.project.orino.domain.planner.dataset.repository;

import ds.project.orino.domain.planner.dataset.entity.DatasetFormula;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DatasetFormulaRepository extends JpaRepository<DatasetFormula, Long> {

    /** 셀 하나의 수식. 한 셀에 수식은 하나(UNIQUE). */
    Optional<DatasetFormula> findByRowIdAndColKey(Long rowId, String colKey);

    /** 페이지 조회 시 그 행들의 수식을 한 번에. */
    List<DatasetFormula> findByRowIdIn(List<Long> rowIds);

    List<DatasetFormula> findByDatasetId(Long datasetId);

    /** 열 삭제 시 그 열의 수식 정리에 사용. */
    List<DatasetFormula> findByDatasetIdAndColKey(Long datasetId, String colKey);

    long countByDatasetId(Long datasetId);

    void deleteByRowIdAndColKey(Long rowId, String colKey);
}
