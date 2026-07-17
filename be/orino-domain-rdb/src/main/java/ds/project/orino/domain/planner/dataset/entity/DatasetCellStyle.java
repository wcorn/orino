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
 * 서식(배경색·정렬)이 지정된 셀. 값이 아니라 <b>서식만</b> 담는다 — 값은
 * {@link DatasetRow#getCells()}에 그대로 남아 읽기 경로가 바뀌지 않는다. 서식 있는 셀만
 * 행이 생기므로 sparse하다({@link DatasetFormula 수식}과 같은 전략).
 *
 * <p>셀 주소는 {@code (rowId, colKey)}다. 행은 {@code row_index}가 아니라 {@code id}로 가리킨다 —
 * 순번은 삽입·삭제 때 밀려 정체성이 될 수 없다.
 *
 * <p>{@code bg}는 팔레트 토큰명(hex 아님)이라 다크모드는 렌더 시 토큰이 해결한다.
 * {@code align}이 null이면 열 기본 정렬을 따른다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "dataset_cell_style")
public class DatasetCellStyle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_id", nullable = false)
    private Long datasetId;

    @Column(name = "row_id", nullable = false)
    private Long rowId;

    @Column(name = "col_key", nullable = false, length = 64)
    private String colKey;

    /** 배경색 토큰명(red/yellow/…). 없으면 null. */
    @Column(length = 32)
    private String bg;

    /** left/center/right. 없으면 null(열 기본 정렬을 따른다). */
    @Column(length = 16)
    private String align;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected DatasetCellStyle() {
    }

    public DatasetCellStyle(Long datasetId, Long rowId, String colKey, String bg, String align) {
        this.datasetId = datasetId;
        this.rowId = rowId;
        this.colKey = colKey;
        this.bg = bg;
        this.align = align;
    }

    /** 서식을 덮어쓴다. 둘 다 null이면 서식 없는 셀 — 호출부가 이 행을 지운다. */
    public void update(String bg, String align) {
        this.bg = bg;
        this.align = align;
    }

    /** 지정된 서식이 하나도 없으면 true. 이 상태면 행을 남길 이유가 없다. */
    public boolean isEmpty() {
        return bg == null && align == null;
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

    public String getBg() {
        return bg;
    }

    public String getAlign() {
        return align;
    }
}
