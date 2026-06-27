package ds.project.orino.domain.planner.dayplan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalTime;

/**
 * 플랜 타임박스 블록. 하루 중 한 구간(또는 시점)을 나타낸다.
 * {@code endTime}이 null이면 시점 블록(예: 기상 알람).
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "day_plan_block")
public class DayPlanBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private DayPlan plan;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false)
    private boolean chime;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected DayPlanBlock() {
    }

    public DayPlanBlock(DayPlan plan, LocalTime startTime, LocalTime endTime,
                        String label, boolean chime, int sortOrder) {
        this.plan = plan;
        this.startTime = startTime;
        this.endTime = endTime;
        this.label = label;
        this.chime = chime;
        this.sortOrder = sortOrder;
    }

    public void update(LocalTime startTime, LocalTime endTime, String label, boolean chime, int sortOrder) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.label = label;
        this.chime = chime;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public DayPlan getPlan() {
        return plan;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getLabel() {
        return label;
    }

    public boolean isChime() {
        return chime;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
