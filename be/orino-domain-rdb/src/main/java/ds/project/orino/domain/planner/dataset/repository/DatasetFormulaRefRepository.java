package ds.project.orino.domain.planner.dataset.repository;

import ds.project.orino.domain.planner.dataset.entity.DatasetFormulaRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DatasetFormulaRefRepository extends JpaRepository<DatasetFormulaRef, Long> {

    /**
     * 무효화 역방향 조회 — 셀 {@code (rowId, colKey)}가 바뀌면 다시 계산해야 할 수식 id.
     *
     * <p>참조 3종을 한 번에 건다:
     * <ul>
     *   <li>{@code SAME_ROW} — 그 열을 같은 행에서 참조. <b>수식이 바로 그 행에 있을 때만</b>
     *       해당하므로 수식의 row_id로 좁힌다. 이게 없으면 다른 행의 계산 열까지 전부 재계산된다.
     *   <li>{@code ABSOLUTE} — 그 셀을 콕 집어 참조.
     *   <li>{@code COLUMN_ALL} — 그 열 전체를 참조(집계). 어느 행이 바뀌든 해당.
     * </ul>
     */
    @Query("SELECT r.formulaId FROM DatasetFormulaRef r, DatasetFormula f "
            + "WHERE r.formulaId = f.id "
            + "AND COALESCE(r.toDatasetId, r.datasetId) = :datasetId AND r.toColKey = :colKey "
            + "AND ("
            + "  (r.toKind = ds.project.orino.domain.planner.dataset.entity.FormulaRefKind.SAME_ROW"
            + "     AND f.rowId = :rowId)"
            + "  OR (r.toKind = ds.project.orino.domain.planner.dataset.entity.FormulaRefKind.ABSOLUTE"
            + "     AND r.toRowId = :rowId)"
            + "  OR r.toKind = ds.project.orino.domain.planner.dataset.entity.FormulaRefKind.COLUMN_ALL"
            + ")")
    List<Long> findDependentFormulaIds(@Param("datasetId") Long datasetId,
                                       @Param("rowId") Long rowId,
                                       @Param("colKey") String colKey);

    /**
     * 열이 통째로 바뀔 때(삭제 등) 그 열을 참조하는 수식 — 종류 불문. 표간 참조도 잡도록 대상
     * 표 기준(COALESCE)으로 조회한다 — 다른 표가 이 열을 참조하면 그것도 무효화 대상이다.
     */
    @Query("SELECT DISTINCT r.formulaId FROM DatasetFormulaRef r "
            + "WHERE COALESCE(r.toDatasetId, r.datasetId) = :datasetId AND r.toColKey = :colKey")
    List<Long> findFormulaIdsReferencingColumn(@Param("datasetId") Long datasetId,
                                               @Param("colKey") String colKey);

    /** 행이 지워질 때 그 행을 콕 집어 참조하던 수식 — #REF! 대상(표간 포함, 대상 표 기준). */
    @Query("SELECT DISTINCT r.formulaId FROM DatasetFormulaRef r "
            + "WHERE COALESCE(r.toDatasetId, r.datasetId) = :datasetId AND r.toRowId = :rowId "
            + "AND r.toKind = ds.project.orino.domain.planner.dataset.entity.FormulaRefKind.ABSOLUTE")
    List<Long> findFormulaIdsReferencingRow(@Param("datasetId") Long datasetId,
                                            @Param("rowId") Long rowId);

    List<DatasetFormulaRef> findByFormulaId(Long formulaId);

    void deleteByFormulaId(Long formulaId);
}
