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
 * 자동 분류 규칙(`LDG-062`) — 「내용에 '스타벅스'가 있으면 카페/간식」.
 *
 * <p><b>가져오기와 수동 입력이 같은 규칙을 쓴다.</b> 그래서 규칙은 화면이 아니라 여기 산다 —
 * 가져오기 쪽에만 두면 손으로 적은 거래는 분류되지 않고, 양쪽에 두면 언젠가 한쪽만 고쳐진다.
 *
 * <p>{@code priority}가 작은 것부터 보고 <b>처음 맞는 하나만</b> 적용한다. 순서가 없으면
 * 「스타벅스」와 「스타」가 모두 걸린 줄의 결과가 실행 순서에 달리고, 그건 사람이 예측할 수 없다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_auto_rule")
public class LedgerAutoRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 120)
    private String keyword;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 20)
    private LedgerMatchType matchType;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private int priority;

    /** 끄기만 하고 지우지 않을 수 있다 — 규칙을 지우면 왜 그런 분류였는지도 사라진다. */
    @Column(nullable = false)
    private boolean enabled = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerAutoRule() {
    }

    public LedgerAutoRule(Long memberId, String keyword, LedgerMatchType matchType,
                          Long categoryId, int priority) {
        this.memberId = memberId;
        this.keyword = keyword;
        this.matchType = matchType;
        this.categoryId = categoryId;
        this.priority = priority;
    }

    public void update(String keyword, LedgerMatchType matchType, Long categoryId,
                       Integer priority, Boolean enabled) {
        if (keyword != null) {
            this.keyword = keyword;
        }
        if (matchType != null) {
            this.matchType = matchType;
        }
        if (categoryId != null) {
            this.categoryId = categoryId;
        }
        if (priority != null) {
            this.priority = priority;
        }
        if (enabled != null) {
            this.enabled = enabled;
        }
    }

    /** 이 규칙이 그 내용에 걸리는가. 꺼 둔 규칙은 언제나 안 걸린다. */
    public boolean matches(String title) {
        return enabled && matchType.matches(title, keyword);
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getKeyword() {
        return keyword;
    }

    public LedgerMatchType getMatchType() {
        return matchType;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
