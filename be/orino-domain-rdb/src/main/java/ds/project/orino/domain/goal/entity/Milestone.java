package ds.project.orino.domain.goal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    @Column(length = 100, nullable = false)
    private String title;

    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private MilestoneStatus status = MilestoneStatus.PENDING;

    @Column(nullable = false)
    private int sortOrder;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Milestone() {
    }

    public Milestone(Goal goal, String title, LocalDate deadline, int sortOrder) {
        this.goal = goal;
        this.title = title;
        this.deadline = deadline;
        this.sortOrder = sortOrder;
    }

    public void update(String title, LocalDate deadline, int sortOrder) {
        this.title = title;
        this.deadline = deadline;
        this.sortOrder = sortOrder;
    }

    public void complete() {
        this.status = MilestoneStatus.COMPLETED;
    }

    public Long getId() {
        return id;
    }

    public Goal getGoal() {
        return goal;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public MilestoneStatus getStatus() {
        return status;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
