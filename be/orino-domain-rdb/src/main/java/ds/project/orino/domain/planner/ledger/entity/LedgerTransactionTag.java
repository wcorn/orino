package ds.project.orino.domain.planner.ledger.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 거래에 붙은 태그 하나. */
@Entity
@Table(name = "ledger_transaction_tag")
public class LedgerTransactionTag {

    @EmbeddedId
    private LedgerTransactionTagId id;

    protected LedgerTransactionTag() {
    }

    public LedgerTransactionTag(Long transactionId, Long tagId) {
        this.id = new LedgerTransactionTagId(transactionId, tagId);
    }

    public LedgerTransactionTagId getId() {
        return id;
    }

    public Long getTransactionId() {
        return id.getTransactionId();
    }

    public Long getTagId() {
        return id.getTagId();
    }
}
