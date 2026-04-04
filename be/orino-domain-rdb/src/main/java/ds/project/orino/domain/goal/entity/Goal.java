package ds.project.orino.domain.goal.entity;

import ds.project.orino.domain.category.entity.Category;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(length = 100, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 15, nullable = false)
    private PeriodType periodType;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private GoalStatus status = GoalStatus.ACTIVE;

    @OneToMany(mappedBy = "goal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Milestone> milestones = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Goal() {
    }

    public Goal(Member member, Category category, String title, String description,
                PeriodType periodType, LocalDate startDate, LocalDate deadline) {
        this.member = member;
        this.category = category;
        this.title = title;
        this.description = description;
        this.periodType = periodType;
        this.startDate = startDate;
        this.deadline = deadline;
    }

    public void update(Category category, String title, String description,
                       PeriodType periodType, LocalDate startDate, LocalDate deadline) {
        this.category = category;
        this.title = title;
        this.description = description;
        this.periodType = periodType;
        this.startDate = startDate;
        this.deadline = deadline;
    }

    public void changeStatus(GoalStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public Category getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public PeriodType getPeriodType() {
        return periodType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public GoalStatus getStatus() {
        return status;
    }

    public List<Milestone> getMilestones() {
        return milestones;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
