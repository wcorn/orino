package ds.project.orino.domain.material.entity;

import ds.project.orino.domain.category.entity.Category;
import ds.project.orino.domain.goal.entity.Goal;
import ds.project.orino.domain.member.entity.Member;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class StudyMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(length = 200, nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private MaterialType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "deadline_mode", length = 10, nullable = false)
    private DeadlineMode deadlineMode = DeadlineMode.FREE;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private MaterialStatus status = MaterialStatus.ACTIVE;

    @OneToMany(mappedBy = "material", cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<StudyUnit> units = new ArrayList<>();

    @OneToOne(mappedBy = "material", cascade = CascadeType.ALL,
            orphanRemoval = true)
    private MaterialAllocation allocation;

    @OneToOne(mappedBy = "material", cascade = CascadeType.ALL,
            orphanRemoval = true)
    private ReviewConfig reviewConfig;

    @OneToMany(mappedBy = "material", cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<MaterialDailyOverride> dailyOverrides = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected StudyMaterial() {
    }

    public StudyMaterial(Member member, String title, MaterialType type,
                         Category category, Goal goal,
                         LocalDate deadline, DeadlineMode deadlineMode) {
        this.member = member;
        this.title = title;
        this.type = type;
        this.category = category;
        this.goal = goal;
        this.deadline = deadline;
        this.deadlineMode = deadlineMode != null
                ? deadlineMode : DeadlineMode.FREE;
    }

    public void update(String title, MaterialType type,
                       Category category, Goal goal,
                       LocalDate deadline, DeadlineMode deadlineMode) {
        this.title = title;
        this.type = type;
        this.category = category;
        this.goal = goal;
        this.deadline = deadline;
        this.deadlineMode = deadlineMode != null
                ? deadlineMode : DeadlineMode.FREE;
    }

    public void shiftDeadline(int days) {
        if (this.deadline != null) {
            this.deadline = this.deadline.plusDays(days);
        }
    }

    public void pause() {
        this.status = MaterialStatus.PAUSED;
    }

    public void resume() {
        this.status = MaterialStatus.ACTIVE;
    }

    public long getCompletedUnits() {
        return units.stream()
                .filter(u -> u.getStatus() == UnitStatus.COMPLETED)
                .count();
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

    public MaterialType getType() {
        return type;
    }

    public Category getCategory() {
        return category;
    }

    public Goal getGoal() {
        return goal;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public DeadlineMode getDeadlineMode() {
        return deadlineMode;
    }

    public MaterialStatus getStatus() {
        return status;
    }

    public List<StudyUnit> getUnits() {
        return units;
    }

    public MaterialAllocation getAllocation() {
        return allocation;
    }

    public ReviewConfig getReviewConfig() {
        return reviewConfig;
    }

    public List<MaterialDailyOverride> getDailyOverrides() {
        return dailyOverrides;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
