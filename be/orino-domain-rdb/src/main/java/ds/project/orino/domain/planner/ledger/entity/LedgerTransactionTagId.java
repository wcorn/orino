package ds.project.orino.domain.planner.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** 거래 ↔ 태그 복합 키. 같은 태그를 같은 거래에 두 번 붙일 수 없음을 DB가 보장한다. */
@Embeddable
public class LedgerTransactionTagId implements Serializable {

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    protected LedgerTransactionTagId() {
    }

    public LedgerTransactionTagId(Long transactionId, Long tagId) {
        this.transactionId = transactionId;
        this.tagId = tagId;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public Long getTagId() {
        return tagId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LedgerTransactionTagId other)) {
            return false;
        }
        return Objects.equals(transactionId, other.transactionId)
                && Objects.equals(tagId, other.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId, tagId);
    }
}
