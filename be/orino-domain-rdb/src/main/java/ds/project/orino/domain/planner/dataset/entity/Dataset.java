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
 * 데이터 그리드 블록의 표 메타. 실제 행 데이터는 {@link DatasetRow}에 분리 저장한다.
 * 노트 content엔 {@code datasetTable{datasetId}} 참조 노드만 담긴다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "dataset")
public class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 열 메타 JSON 문자열: {@code [{"key","label"}, ...]}. */
    @Column(name = "columns_json", columnDefinition = "JSON", nullable = false)
    private String columns;

    /** 행 수(비정규화, 조회 최적화). */
    @Column(name = "row_count", nullable = false)
    private int rowCount;

    /**
     * 다음 열 key 발급 번호. key는 cells 맵의 주소라 재사용하면 지워진 열의 값이 되살아나므로,
     * 열이 삭제돼도 되돌리지 않고 한 방향으로만 올린다.
     */
    @Column(name = "next_column_seq", nullable = false)
    private int nextColumnSeq;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected Dataset() {
    }

    public Dataset(Long memberId, String columns, int nextColumnSeq) {
        this.memberId = memberId;
        this.columns = columns;
        this.rowCount = 0;
        this.nextColumnSeq = nextColumnSeq;
    }

    public void updateColumns(String columns) {
        this.columns = columns;
    }

    /** 열 key 하나를 발급하고 카운터를 올린다. */
    public int issueColumnSeq() {
        return nextColumnSeq++;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getColumns() {
        return columns;
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getNextColumnSeq() {
        return nextColumnSeq;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
