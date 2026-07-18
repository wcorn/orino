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
 * 병합된 셀 영역. 값이 아니라 <b>span만</b> 담는다 — 덮인 셀의 값은
 * {@link DatasetRow#getCells()}에 그대로 남아 분할하면 되살아나고 읽기 경로가 바뀌지 않는다.
 * 병합 있는 앵커만 행이 생기므로 sparse하다({@link DatasetFormula 수식}·{@link DatasetCellStyle 서식}과
 * 같은 오버레이 전략).
 *
 * <p>앵커는 <b>좌상단</b> 셀이고 주소는 {@code (anchorRowId, anchorColKey)}다. 행은 {@code row_index}가
 * 아니라 {@code id}로 가리킨다 — 순번은 삽입·삭제 때 밀려 정체성이 될 수 없다.
 *
 * <p>{@code rowSpan}·{@code colSpan}은 앵커가 덮는 칸 수(각 &ge;1). 슬라이스 1(가로 병합)은
 * {@code rowSpan}이 항상 1이다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "dataset_merge")
public class DatasetMerge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_id", nullable = false)
    private Long datasetId;

    @Column(name = "anchor_row_id", nullable = false)
    private Long anchorRowId;

    @Column(name = "anchor_col_key", nullable = false, length = 64)
    private String anchorColKey;

    @Column(name = "row_span", nullable = false)
    private int rowSpan;

    @Column(name = "col_span", nullable = false)
    private int colSpan;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected DatasetMerge() {
    }

    public DatasetMerge(Long datasetId, Long anchorRowId, String anchorColKey,
                        int rowSpan, int colSpan) {
        this.datasetId = datasetId;
        this.anchorRowId = anchorRowId;
        this.anchorColKey = anchorColKey;
        this.rowSpan = rowSpan;
        this.colSpan = colSpan;
    }

    /** span을 덮어쓴다(병합 범위 변경). */
    public void update(int rowSpan, int colSpan) {
        this.rowSpan = rowSpan;
        this.colSpan = colSpan;
    }

    public Long getId() {
        return id;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public Long getAnchorRowId() {
        return anchorRowId;
    }

    public String getAnchorColKey() {
        return anchorColKey;
    }

    public int getRowSpan() {
        return rowSpan;
    }

    public int getColSpan() {
        return colSpan;
    }
}
