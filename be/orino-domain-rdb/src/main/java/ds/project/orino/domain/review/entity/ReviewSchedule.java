package ds.project.orino.domain.review.entity;

import ds.project.orino.domain.material.entity.StudyUnit;
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
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_schedule")
@EntityListeners(AuditingEntityListener.class)
public class ReviewSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_unit_id", nullable = false)
    private StudyUnit studyUnit;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(nullable = false)
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private ReviewStatus status = ReviewStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private DifficultyFeedback difficultyFeedback;

    private LocalDateTime completedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected ReviewSchedule() {
    }

    public ReviewSchedule(StudyUnit studyUnit, int sequence,
                          LocalDate scheduledDate) {
        this.studyUnit = studyUnit;
        this.sequence = sequence;
        this.scheduledDate = scheduledDate;
    }

    public void markOverdue() {
        this.status = ReviewStatus.OVERDUE;
    }

    public void complete(DifficultyFeedback feedback) {
        this.status = ReviewStatus.COMPLETED;
        this.difficultyFeedback = feedback;
        this.completedAt = LocalDateTime.now();
    }

    public void skip() {
        this.status = ReviewStatus.SKIPPED;
    }

    public void reschedule(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public Long getId() {
        return id;
    }

    public StudyUnit getStudyUnit() {
        return studyUnit;
    }

    public int getSequence() {
        return sequence;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public DifficultyFeedback getDifficultyFeedback() {
        return difficultyFeedback;
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
