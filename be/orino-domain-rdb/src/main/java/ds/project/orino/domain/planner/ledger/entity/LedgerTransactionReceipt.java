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
 * 영수증 첨부(`LDG-016`). 거래 1건에 여러 장이 붙는다.
 *
 * <p>바이트는 MinIO에 있고 여기에는 <b>키만</b> 남는다. 일상기록과 같은 버킷을 쓰고 prefix만
 * 다르다 — 새 저장소를 만들지 않는다.
 *
 * <p>거래를 소프트 삭제해도 <b>이 행과 오브젝트를 즉시 지우지 않는다.</b> 되돌리기가 가능해야
 * 하기 때문이다. 지운 뒤에 되살렸는데 영수증만 사라져 있으면 그것도 원장이 상한 것이다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_transaction_receipt")
public class LedgerTransactionReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "object_key", nullable = false, length = 255)
    private String objectKey;

    @Column(name = "content_type", length = 60)
    private String contentType;

    @Column(name = "byte_size")
    private Long byteSize;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerTransactionReceipt() {
    }

    public LedgerTransactionReceipt(Long memberId, Long transactionId, String objectKey,
                                    String contentType, Long byteSize, int displayOrder) {
        this.memberId = memberId;
        this.transactionId = transactionId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getByteSize() {
        return byteSize;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
