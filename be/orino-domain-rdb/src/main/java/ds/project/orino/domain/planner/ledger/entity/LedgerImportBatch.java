package ds.project.orino.domain.planner.ledger.entity;

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
 * 한 번의 가져오기(`LDG-093`).
 *
 * <p><b>되돌릴 수 있는 단위다.</b> 잘못 들어간 파일 하나가 잔액·통계·청구서를 전부 틀어
 * 놓는데, 그걸 손으로 되짚어 지우는 것은 사실상 불가능하다 — 어느 줄이 이번에 들어온
 * 것인지 나중에는 구별되지 않기 때문이다.
 *
 * <p>되돌린 배치도 <b>행이 남는다.</b> {@code revertedAt}만 찍힌다 — 「무엇을 넣었다가
 * 물렀는지」도 이력이고, 지우면 같은 파일을 또 넣는 날 알 수 없다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_import_batch")
public class LedgerImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 60)
    private String source;

    @Column(name = "file_name")
    private String fileName;

    /** 파일에서 읽은 줄. 사람이 체크를 해제한 줄도 여기엔 들어 있다. */
    @Column(name = "row_count", nullable = false)
    private int rowCount;

    /** 실제로 원장에 들어간 줄. {@code rowCount}와 다른 것이 정상이다. */
    @Column(name = "inserted_count", nullable = false)
    private int insertedCount;

    @Column(name = "reverted_at")
    private Instant revertedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerImportBatch() {
    }

    public LedgerImportBatch(Long memberId, String source, String fileName, int rowCount) {
        this.memberId = memberId;
        this.source = source;
        this.fileName = fileName;
        this.rowCount = rowCount;
    }

    public void markInserted(int insertedCount) {
        this.insertedCount = insertedCount;
    }

    public void revert(Instant at) {
        this.revertedAt = at;
    }

    public boolean isReverted() {
        return revertedAt != null;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getSource() {
        return source;
    }

    public String getFileName() {
        return fileName;
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getInsertedCount() {
        return insertedCount;
    }

    public Instant getRevertedAt() {
        return revertedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
