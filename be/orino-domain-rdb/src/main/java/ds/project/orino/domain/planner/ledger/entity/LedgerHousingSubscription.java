package ds.project.orino.domain.planner.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.YearMonth;

/**
 * 청약홈 기준값(`LDG-007`). 자산과 1:1이라 {@code asset_id}가 곧 PK다(D-20).
 *
 * <p><b>인정 회차·금액은 저장하지 않는다.</b> 조회할 때 기준값 + 이후 원장으로 센다 —
 * 원장으로 계산할 수 있는 값을 저장해 두면 원장과 갈라진다(D-8). 기준값은 원장으로 계산할 수
 * 없는 <b>외부 조회 값</b>이라 여기 둔다(D-18).
 *
 * <p>기준값은 <b>돈이 아니다.</b> 잔액에 영향을 주지 않고 거래를 만들지 않는다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_housing_subscription")
public class LedgerHousingSubscription {

    @Id
    @Column(name = "asset_id")
    private Long assetId;

    /** 청약홈 인정 회차. 아래 둘과 함께 있거나 함께 없다. */
    @Column(name = "baseline_count")
    private Integer baselineCount;

    /** 청약홈 인정 금액. */
    @Column(name = "baseline_amount")
    private Long baselineAmount;

    /**
     * 기준값이 몇 월분까지인지({@code 2026-08}). 이 다음 달부터 원장에서 센다.
     *
     * <p>DB가 {@code CHAR(7)}이다. 타입 코드를 맞추지 않으면 Hibernate가 {@code VARCHAR}로
     * 보고 {@code validate}가 기동 시점에 깨진다.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "baseline_through_month", length = 7)
    private String baselineThroughMonth;

    @Column(name = "baseline_updated_at")
    private Instant baselineUpdatedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerHousingSubscription() {
    }

    public LedgerHousingSubscription(Long assetId) {
        this.assetId = assetId;
    }

    /** 세 값을 <b>한꺼번에</b> 바꾼다. 하나만 바꾸는 길을 열면 어디서부터 셀지 모르는 행이 생긴다. */
    public void updateBaseline(int count, long amount, YearMonth throughMonth, Instant updatedAt) {
        this.baselineCount = count;
        this.baselineAmount = amount;
        this.baselineThroughMonth = throughMonth.toString();
        this.baselineUpdatedAt = updatedAt;
    }

    public boolean hasBaseline() {
        return baselineCount != null && baselineAmount != null && baselineThroughMonth != null;
    }

    public Long getAssetId() {
        return assetId;
    }

    public Integer getBaselineCount() {
        return baselineCount;
    }

    public Long getBaselineAmount() {
        return baselineAmount;
    }

    public YearMonth getBaselineThroughMonth() {
        return baselineThroughMonth == null ? null : YearMonth.parse(baselineThroughMonth);
    }

    public Instant getBaselineUpdatedAt() {
        return baselineUpdatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
