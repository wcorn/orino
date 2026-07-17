package ds.project.orino.domain.planner.dataset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 수식이 있는 셀. 값이 아니라 <b>수식만</b> 담는다 — 계산된 값은 {@link DatasetRow#getCells()}에
 * 그대로 남아 읽기 경로가 바뀌지 않는다. 수식 있는 셀만 행이 생기므로 sparse하다.
 *
 * <p>셀 주소는 {@code (rowId, colKey)}다. 행은 {@code row_index}가 아니라 {@code id}로 가리킨다 —
 * 순번은 삽입·삭제 때 밀려 정체성이 될 수 없다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "dataset_formula")
public class DatasetFormula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_id", nullable = false)
    private Long datasetId;

    @Column(name = "row_id", nullable = false)
    private Long rowId;

    @Column(name = "col_key", nullable = false, length = 64)
    private String colKey;

    /** 바인딩 정규형(열 key·행 id로 해석된 형태). 표시용 문자열은 읽을 때 만든다. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String raw;

    /** {@code #REF!}, {@code #VALUE!}, {@code #DIV/0!} 등. 정상이면 null. */
    @Column(length = 32)
    private String error;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected DatasetFormula() {
    }

    public DatasetFormula(Long datasetId, Long rowId, String colKey, String raw) {
        this.datasetId = datasetId;
        this.rowId = rowId;
        this.colKey = colKey;
        this.raw = raw;
    }

    public void updateRaw(String raw) {
        this.raw = raw;
        this.error = null;
    }

    public void markError(String error) {
        this.error = error;
    }

    public void clearError() {
        this.error = null;
    }

    public Long getId() {
        return id;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public Long getRowId() {
        return rowId;
    }

    public String getColKey() {
        return colKey;
    }

    public String getRaw() {
        return raw;
    }

    public String getError() {
        return error;
    }
}
