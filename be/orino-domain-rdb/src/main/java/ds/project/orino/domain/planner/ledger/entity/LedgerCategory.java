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
 * 카테고리. <b>2단까지만</b>이고 {@link LedgerFlow}별로 세트가 분리된다.
 *
 * <p>깊이를 열어 두면 「식비 &gt; 외식 &gt; 점심 &gt; 회사 근처」까지 파고들어 결국 아무도
 * 분류하지 않게 된다. 3단은 거부한다(LDG-ERR-015).
 *
 * <p>통합·이름 변경 시 <b>내역이 함께 따라간다</b>. 통합은 거래의 {@code categoryId}를 옮기고
 * 원본을 {@link #archive()}로 남기는 방식이다 — 거래를 지우지 않는다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_category")
public class LedgerCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** NULL이면 대분류. 이 값이 가리키는 카테고리는 반드시 대분류여야 한다. */
    @Column(name = "parent_id")
    private Long parentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerFlow flow;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(length = 20)
    private String color;

    /** lucide 아이콘명. 화면이 그대로 렌더한다. */
    @Column(length = 40)
    private String icon;

    /**
     * 고정비인지 변동비인지. <b>NULL이면 아직 정하지 않았다</b>는 뜻이다(v2).
     *
     * <p>기본값을 주지 않는 이유는 「모른다」와 「변동비다」가 다르기 때문이다 — 기본값을
     * 변동비로 두면 아무도 분류하지 않은 가계부에서 「변동비가 100%」라는 거짓말이 나온다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "cost_type", length = 20)
    private LedgerCostType costType;

    /** 카드 실적에서 뺀다. 세금·보험료처럼 카드사가 실적으로 안 세는 것들(확정 명세 §7.6). */
    @Column(name = "exclude_from_card_goal", nullable = false)
    private boolean excludeFromCardGoal;

    /** 연간 결산에서 뺀다. 저축·투자처럼 「쓴 돈」이 아니라 자산 이동인 것들. */
    @Column(name = "exclude_from_settlement", nullable = false)
    private boolean excludeFromSettlement;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean archived;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerCategory() {
    }

    public LedgerCategory(Long memberId, LedgerFlow flow, String name, Long parentId, int displayOrder) {
        this.memberId = memberId;
        this.flow = flow;
        this.name = name;
        this.parentId = parentId;
        this.displayOrder = displayOrder;
        this.archived = false;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateParentId(Long parentId) {
        this.parentId = parentId;
    }

    public void updateColor(String color) {
        this.color = color;
    }

    public void updateIcon(String icon) {
        this.icon = icon;
    }

    public void updateDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    /** 통합·삭제의 결과다. 행을 지우지 않는다 — 지우면 과거 통계에서 이름이 사라진다. */
    public void archive() {
        this.archived = true;
    }

    public boolean isRoot() {
        return parentId == null;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getParentId() {
        return parentId;
    }

    public LedgerFlow getFlow() {
        return flow;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public String getIcon() {
        return icon;
    }

    public LedgerCostType getCostType() {
        return costType;
    }

    public boolean isExcludeFromCardGoal() {
        return excludeFromCardGoal;
    }

    public boolean isExcludeFromSettlement() {
        return excludeFromSettlement;
    }

    /** 속성 셋을 함께 바꾼다 — 화면에서도 한 줄로 다루는 값들이다. */
    public void updateAttributes(LedgerCostType costType,
                                 boolean excludeFromCardGoal,
                                 boolean excludeFromSettlement) {
        this.costType = costType;
        this.excludeFromCardGoal = excludeFromCardGoal;
        this.excludeFromSettlement = excludeFromSettlement;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isArchived() {
        return archived;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
