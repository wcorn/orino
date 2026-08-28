package ds.project.orino.domain.planner.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 빠른 입력 템플릿(`LDG-013`) — `출근 커피 4,500 / 카페 / 신한카드`.
 *
 * <p><b>날짜를 담지 않는다.</b> 템플릿으로 적는 건은 언제나 「오늘」이다 — 날짜까지 저장해 두면
 * 한 번 눌러 기록한다는 목적이 사라진다.
 *
 * <p>노출 순서는 {@code useCount}가 정한다. 사람이 순서를 관리하게 하면 그것 자체가 새 일이 되고,
 * 마찰을 줄이려고 만든 기능이 마찰을 만든다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_transaction_template")
public class LedgerTransactionTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "tx_type", nullable = false, length = 20)
    private LedgerFlow txType;

    @Column(nullable = false)
    private long amount;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    /** 비워 둘 수 있다 — 미분류로 적히는 템플릿도 있을 수 있다. */
    @Column(name = "category_id")
    private Long categoryId;

    @Column(length = 120)
    private String title;

    @Column(name = "use_count", nullable = false)
    private int useCount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerTransactionTemplate() {
    }

    public LedgerTransactionTemplate(Long memberId, String name, LedgerFlow txType,
                                     long amount, Long assetId) {
        this.memberId = memberId;
        this.name = name;
        this.txType = txType;
        this.amount = amount;
        this.assetId = assetId;
        this.useCount = 0;
    }

    public void update(String name, LedgerFlow txType, Long amount,
                       Long assetId, Long categoryId, String title) {
        if (name != null) {
            this.name = name;
        }
        if (txType != null) {
            this.txType = txType;
        }
        if (amount != null) {
            this.amount = amount;
        }
        if (assetId != null) {
            this.assetId = assetId;
        }
        if (categoryId != null) {
            this.categoryId = categoryId;
        }
        if (title != null) {
            this.title = title;
        }
    }

    public void updateCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    /** 이 템플릿으로 한 건 적을 때마다 오른다. 대시보드 칩의 순서가 여기서 나온다. */
    public void recordUse() {
        this.useCount++;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getName() {
        return name;
    }

    public LedgerFlow getTxType() {
        return txType;
    }

    public long getAmount() {
        return amount;
    }

    public Long getAssetId() {
        return assetId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getTitle() {
        return title;
    }

    public int getUseCount() {
        return useCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
