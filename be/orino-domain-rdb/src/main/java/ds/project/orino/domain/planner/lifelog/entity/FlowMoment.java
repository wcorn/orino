package ds.project.orino.domain.planner.lifelog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 흐름↔기록 N:M 조인. 한 기록이 여러 흐름에 동시에 담길 수 있다. 흐름 내 순서는 기본 occurred_at
 * 순이되 사용자가 조정하면 {@code sortOrder}가 우선한다. (flow_id, moment_id)는 유니크라 담기는 멱등.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "flow_moment")
public class FlowMoment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flow_id", nullable = false)
    private Long flowId;

    @Column(name = "moment_id", nullable = false)
    private Long momentId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @CreatedDate
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected FlowMoment() {
    }

    public FlowMoment(Long flowId, Long momentId, int sortOrder) {
        this.flowId = flowId;
        this.momentId = momentId;
        this.sortOrder = sortOrder;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getFlowId() {
        return flowId;
    }

    public Long getMomentId() {
        return momentId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
