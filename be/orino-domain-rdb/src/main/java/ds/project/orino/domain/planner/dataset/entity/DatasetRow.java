package ds.project.orino.domain.planner.dataset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 데이터셋의 한 행. {@code cells}는 열 key를 주소로 쓰는 맵 JSON({@code {"c0":"a","c1":"b"}}).
 * 위치가 아닌 key에 값을 묶어, 열 추가·삭제·순서변경이 행을 건드리지 않게 한다.
 * {@code rowIndex}(0-base)로 정렬·페이지네이션한다. dataset 삭제 시 cascade.
 */
@Entity
@Table(name = "dataset_row")
public class DatasetRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_id", nullable = false)
    private Long datasetId;

    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    @Column(columnDefinition = "JSON", nullable = false)
    private String cells;

    /** 표시 높이(px). null이면 기본 높이. 열 너비처럼 값 있는 행만 담기는 sparse 속성. */
    @Column
    private Integer height;

    protected DatasetRow() {
    }

    public DatasetRow(Long datasetId, int rowIndex, String cells) {
        this.datasetId = datasetId;
        this.rowIndex = rowIndex;
        this.cells = cells;
    }

    public void updateCells(String cells) {
        this.cells = cells;
    }

    /** 행 높이를 바꾼다. null이면 기본 높이로 되돌린다. */
    public void updateHeight(Integer height) {
        this.height = height;
    }

    public void updateRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public Long getId() {
        return id;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public String getCells() {
        return cells;
    }

    public Integer getHeight() {
        return height;
    }
}
