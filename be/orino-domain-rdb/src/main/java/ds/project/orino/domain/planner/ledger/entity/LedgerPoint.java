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
import java.time.LocalDate;

/**
 * 포인트·마일리지(`LDG-006`).
 *
 * <p><b>이 테이블은 원장과 조인되지 않는다.</b> 총자산·순자산·통계 어디에도 들어가지 않고,
 * 조인해야 할 이유가 생겼다면 그건 설계가 틀어졌다는 신호다 — 포인트는 쓸 수 있는 곳이 정해진
 * 외상이지 돈이 아니고, 섞는 순간 「자산이 얼마인가」가 답할 수 없는 질문이 된다.
 *
 * <p>그래서 원화로 환산하지도 않는다. 환산값을 갖는 순간 어딘가에서 더하고 싶어진다.
 * 적어 두는 이유의 절반은 {@code expiresOn} — 언제 사라지는지다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_point")
public class LedgerPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 60)
    private String name;

    /** 「포인트」·「마일」 같은 단위. 서로 더할 수 없다는 사실을 화면에 남기는 값이다. */
    @Column(nullable = false, length = 20)
    private String unit;

    @Column(nullable = false)
    private long balance;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(length = 255)
    private String memo;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerPoint() {
    }

    public LedgerPoint(Long memberId, String name, String unit, long balance,
                       LocalDate expiresOn, String memo, int displayOrder) {
        this.memberId = memberId;
        this.name = name;
        this.unit = unit;
        this.balance = balance;
        this.expiresOn = expiresOn;
        this.memo = memo;
        this.displayOrder = displayOrder;
    }

    public void update(String name, String unit, Long balance, LocalDate expiresOn,
                       boolean clearExpiry, String memo, Integer displayOrder) {
        if (name != null) {
            this.name = name;
        }
        if (unit != null) {
            this.unit = unit;
        }
        if (balance != null) {
            this.balance = balance;
        }
        // 소멸일을 지우는 것과 안 건드리는 것은 다르다 — null 하나로는 구별되지 않는다.
        if (clearExpiry) {
            this.expiresOn = null;
        } else if (expiresOn != null) {
            this.expiresOn = expiresOn;
        }
        if (memo != null) {
            this.memo = memo;
        }
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
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

    public String getUnit() {
        return unit;
    }

    public long getBalance() {
        return balance;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public String getMemo() {
        return memo;
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
