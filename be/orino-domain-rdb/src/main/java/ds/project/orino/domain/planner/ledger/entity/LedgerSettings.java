package ds.project.orino.domain.planner.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 회원별 가계부 설정. 회원당 하나뿐이라 {@code member_id}가 곧 PK다.
 *
 * <p>{@code monthStartDay}는 <b>예산 기간에만</b> 쓴다(확정 명세 §9). 카드 정산 사이클과
 * 정기 항목 주기는 이 값에 영향받지 않는다 — 급여일을 25일로 잡았다고 카드 결제일이
 * 따라 움직이면 그건 원장이 아니라 추측이다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_settings")
public class LedgerSettings {

    /** 말일을 뜻하는 특수값. 2월과 31일 달을 한 값으로 표현하려면 이 방법뿐이다. */
    public static final int LAST_DAY_OF_MONTH = 99;

    @Id
    @Column(name = "member_id")
    private Long memberId;

    /**
     * 1~28 또는 {@link #LAST_DAY_OF_MONTH}. 29~31은 없는 달이 있어 허용하지 않는다.
     *
     * <p>DB는 {@code TINYINT}인데 Hibernate는 {@code int}를 {@code INTEGER}로 매핑한다 —
     * 그대로 두면 {@code validate}가 기동 시점에 깨진다. 자릿수가 아니라 <b>타입이 맞아야</b> 한다.
     */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "month_start_day", nullable = false)
    private int monthStartDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "month_start_weekend_policy", nullable = false, length = 20)
    private LedgerMonthStartWeekendPolicy monthStartWeekendPolicy;

    @Column(name = "default_asset_id")
    private Long defaultAssetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_perspective", nullable = false, length = 20)
    private LedgerPerspective defaultPerspective;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerSettings() {
    }

    public LedgerSettings(Long memberId) {
        this.memberId = memberId;
        this.monthStartDay = 1;
        this.monthStartWeekendPolicy = LedgerMonthStartWeekendPolicy.AS_IS;
        this.defaultPerspective = LedgerPerspective.SPEND;
    }

    public void updateMonthStartDay(int monthStartDay) {
        this.monthStartDay = monthStartDay;
    }

    public void updateMonthStartWeekendPolicy(LedgerMonthStartWeekendPolicy policy) {
        this.monthStartWeekendPolicy = policy;
    }

    public void updateDefaultAssetId(Long defaultAssetId) {
        this.defaultAssetId = defaultAssetId;
    }

    public void updateDefaultPerspective(LedgerPerspective defaultPerspective) {
        this.defaultPerspective = defaultPerspective;
    }

    public Long getMemberId() {
        return memberId;
    }

    public int getMonthStartDay() {
        return monthStartDay;
    }

    public LedgerMonthStartWeekendPolicy getMonthStartWeekendPolicy() {
        return monthStartWeekendPolicy;
    }

    public Long getDefaultAssetId() {
        return defaultAssetId;
    }

    public LedgerPerspective getDefaultPerspective() {
        return defaultPerspective;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
