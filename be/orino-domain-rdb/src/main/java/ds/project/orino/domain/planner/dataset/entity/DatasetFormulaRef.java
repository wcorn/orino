package ds.project.orino.domain.planner.dataset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 수식이 무엇을 참조하는지. 역방향으로 조회해 "이 셀이 바뀌면 무엇을 다시 계산할지"를 얻는다.
 *
 * <p>{@code toRowId}엔 FK가 없다. 참조하던 행이 지워질 때 cascade로 이 행까지 사라지면
 * "지워진 행을 참조했다"는 사실을 잃어 {@code #REF!}를 만들 수 없다 —
 * 끊긴 참조로 남겨두는 것이 의도된 상태다.
 */
@Entity
@Table(name = "dataset_formula_ref")
public class DatasetFormulaRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "formula_id", nullable = false)
    private Long formulaId;

    /** 역방향 조회를 dataset 안으로 좁히기 위한 비정규화. */
    @Column(name = "dataset_id", nullable = false)
    private Long datasetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_kind", nullable = false, length = 16)
    private FormulaRefKind toKind;

    /** {@link FormulaRefKind#ABSOLUTE}일 때만 값. 나머지는 null. */
    @Column(name = "to_row_id")
    private Long toRowId;

    @Column(name = "to_col_key", nullable = false, length = 64)
    private String toColKey;

    /**
     * 표간 참조({요약!환율}1)의 대상 표. null이면 같은 표(기존). FK를 두지 않아 대상 표가
     * 지워져도 참조가 끊긴 채 남아 #REF!의 근거가 된다(to_row_id와 같은 규칙).
     */
    @Column(name = "to_dataset_id")
    private Long toDatasetId;

    protected DatasetFormulaRef() {
    }

    private DatasetFormulaRef(Long formulaId, Long datasetId, FormulaRefKind toKind,
                             Long toRowId, String toColKey, Long toDatasetId) {
        this.formulaId = formulaId;
        this.datasetId = datasetId;
        this.toKind = toKind;
        this.toRowId = toRowId;
        this.toColKey = toColKey;
        this.toDatasetId = toDatasetId;
    }

    /** 같은 행의 열 참조({@code =c0*c1}). */
    public static DatasetFormulaRef sameRow(Long formulaId, Long datasetId, String toColKey) {
        return new DatasetFormulaRef(formulaId, datasetId, FormulaRefKind.SAME_ROW, null, toColKey,
                null);
    }

    /** 특정 행의 열 참조({@code =B5}). */
    public static DatasetFormulaRef absolute(Long formulaId, Long datasetId, Long toRowId,
                                             String toColKey) {
        return new DatasetFormulaRef(formulaId, datasetId, FormulaRefKind.ABSOLUTE, toRowId,
                toColKey, null);
    }

    /** 열 전체 참조({@code =SUM(c2)}). */
    public static DatasetFormulaRef columnAll(Long formulaId, Long datasetId, String toColKey) {
        return new DatasetFormulaRef(formulaId, datasetId, FormulaRefKind.COLUMN_ALL, null,
                toColKey, null);
    }

    /** 표간 절대셀 참조({@code ={요약!환율}1}). 대상 표({@code toDatasetId})의 특정 행·열. */
    public static DatasetFormulaRef crossAbsolute(Long formulaId, Long datasetId, Long toDatasetId,
                                                  Long toRowId, String toColKey) {
        return new DatasetFormulaRef(formulaId, datasetId, FormulaRefKind.ABSOLUTE, toRowId,
                toColKey, toDatasetId);
    }

    public Long getId() {
        return id;
    }

    public Long getFormulaId() {
        return formulaId;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public FormulaRefKind getToKind() {
        return toKind;
    }

    public Long getToRowId() {
        return toRowId;
    }

    public String getToColKey() {
        return toColKey;
    }

    /** 표간 참조의 대상 표. null이면 같은 표. */
    public Long getToDatasetId() {
        return toDatasetId;
    }
}
