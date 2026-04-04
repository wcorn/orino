package ds.project.orino.domain.todo.entity;

import ds.project.orino.domain.category.entity.Category;
import ds.project.orino.domain.goal.entity.Goal;
import ds.project.orino.domain.member.entity.Member;
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
public class Todo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private Priority priority = Priority.MEDIUM;

    private LocalDate deadline;

    private Integer estimatedMinutes;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private TodoStatus status = TodoStatus.PENDING;

    private LocalDateTime completedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Todo() {
    }

    public Todo(Member member, String title, String description,
                Category category, Goal goal, Priority priority,
                LocalDate deadline, Integer estimatedMinutes) {
        this.member = member;
        this.title = title;
        this.description = description;
        this.category = category;
        this.goal = goal;
        this.priority = priority != null ? priority : Priority.MEDIUM;
        this.deadline = deadline;
        this.estimatedMinutes = estimatedMinutes;
    }

    public void update(String title, String description,
                       Category category, Goal goal, Priority priority,
                       LocalDate deadline, Integer estimatedMinutes) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.goal = goal;
        this.priority = priority != null ? priority : Priority.MEDIUM;
        this.deadline = deadline;
        this.estimatedMinutes = estimatedMinutes;
    }

    public void complete() {
        this.status = TodoStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public Goal getGoal() {
        return goal;
    }

    public Priority getPriority() {
        return priority;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public TodoStatus getStatus() {
        return status;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
