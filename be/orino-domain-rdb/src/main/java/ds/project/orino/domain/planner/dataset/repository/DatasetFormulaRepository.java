package ds.project.orino.domain.planner.dataset.repository;

import ds.project.orino.domain.planner.dataset.entity.DatasetFormula;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    long countByDatasetIdAndColKey(Long datasetId, String colKey);

    /**
     * 그 열 수식들의 서로 다른 저장형 개수. 1이면 모든 행이 같은 수식 = 계산 열이다.
     *
     * <p>전부 읽어 비교하면 행 수만큼 비싸다 — 행을 추가할 때마다 하는 판정이라 쿼리로 센다.
     * 저장형은 같은 행 참조에 행 id를 안 쓰므로(D9), 계산 열이면 모든 행의 raw가 글자까지 같다.
     */
    @Query("SELECT COUNT(DISTINCT f.raw) FROM DatasetFormula f "
            + "WHERE f.datasetId = :datasetId AND f.colKey = :colKey")
    long countDistinctRawByColumn(@Param("datasetId") Long datasetId,
                                  @Param("colKey") String colKey);

    void deleteByRowIdAndColKey(Long rowId, String colKey);
}
