package ds.project.orino.domain.planner.review.entity;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_schedule")
@EntityListeners(AuditingEntityListener.class)
public class ReviewSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "study_unit_id", nullable = false)
    private Long studyUnitId;

    @Column(nullable = false)
    private Integer sequence;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "interval_days", nullable = false)
    private Integer intervalDays;

    @Column(name = "ease_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal easeFactor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReviewStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Rating rating;

    @Column(name = "elapsed_days")
    private Integer elapsedDays;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected ReviewSchedule() {
    }

    public ReviewSchedule(Long memberId, Long studyUnitId, int sequence,
                          LocalDate scheduledDate, int intervalDays, BigDecimal easeFactor) {
        this.memberId = memberId;
        this.studyUnitId = studyUnitId;
        this.sequence = sequence;
        this.scheduledDate = scheduledDate;
        this.intervalDays = intervalDays;
        this.easeFactor = easeFactor;
        this.status = ReviewStatus.PENDING;
    }

    public void complete(Rating rating, LocalDate today, LocalDateTime now) {
        this.status = ReviewStatus.COMPLETED;
        this.rating = rating;
        this.completedAt = now;
        this.elapsedDays = (int) (today.toEpochDay() - scheduledDate.toEpochDay());
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getStudyUnitId() {
        return studyUnitId;
    }

    public Integer getSequence() {
        return sequence;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public Integer getIntervalDays() {
        return intervalDays;
    }

    public BigDecimal getEaseFactor() {
        return easeFactor;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public Rating getRating() {
        return rating;
    }

    public Integer getElapsedDays() {
        return elapsedDays;
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
