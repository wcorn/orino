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
}
