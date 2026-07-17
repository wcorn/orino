package ds.project.orino.domain.planner.dataset.repository;

import ds.project.orino.domain.planner.dataset.entity.DatasetCellStyle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DatasetCellStyleRepository extends JpaRepository<DatasetCellStyle, Long> {

    /** 셀 하나의 서식. 한 셀에 서식은 하나(UNIQUE). */
    Optional<DatasetCellStyle> findByRowIdAndColKey(Long rowId, String colKey);

    /** 페이지 조회 시 그 행들의 서식을 한 번에. */
    List<DatasetCellStyle> findByRowIdIn(List<Long> rowIds);

    /** 열 삭제 시 그 열의 서식 정리에 사용. */
    List<DatasetCellStyle> findByDatasetIdAndColKey(Long datasetId, String colKey);

    void deleteByDatasetIdAndColKey(Long datasetId, String colKey);
}
