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
 * 자산 그룹(`국민은행` · `신한카드` · `그 외`).
 *
 * <p>접힘 상태를 서버에 둔다 — 기기를 바꿔도 접어 둔 그룹이 그대로여야 하고,
 * 자산 목록은 폰과 데스크톱을 오가며 보는 화면이다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_asset_group")
public class LedgerAssetGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerAssetGroupKind kind;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean collapsed;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerAssetGroup() {
    }

    public LedgerAssetGroup(Long memberId, String name, LedgerAssetGroupKind kind, int displayOrder) {
        this.memberId = memberId;
        this.name = name;
        this.kind = kind;
        this.displayOrder = displayOrder;
        this.collapsed = false;
    }

    /** null인 필드는 건드리지 않는다 — PATCH는 「보낸 것만 바꾼다」. */
    public void update(String name, LedgerAssetGroupKind kind, Integer displayOrder, Boolean collapsed) {
        if (name != null) {
            this.name = name;
        }
        if (kind != null) {
            this.kind = kind;
        }
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }
        if (collapsed != null) {
            this.collapsed = collapsed;
        }
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

    public LedgerAssetGroupKind getKind() {
        return kind;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
