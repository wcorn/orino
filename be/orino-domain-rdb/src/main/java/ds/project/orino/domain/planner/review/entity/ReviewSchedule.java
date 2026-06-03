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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "review_schedule")
public class ReviewSchedule {

    public static final BigDecimal INITIAL_EASE_FACTOR = new BigDecimal("2.50");

    /** 다중일 복습 due 시각(롤오버). 해당 날짜 04:00부터 due가 되어 그 날 하루 종일 복습 가능. */
    public static final int ROLLOVER_HOUR = 4;

    /** AGAIN(틀림) 시 당일 재복습까지의 분. */
    public static final int RELEARN_MINUTES = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "flashcard_id", nullable = false)
    private Long flashcardId;

    @Column(nullable = false)
    private Integer sequence;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

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
    private Instant completedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected ReviewSchedule() {
    }

    public ReviewSchedule(Long memberId, Long flashcardId, int sequence,
                          Instant scheduledAt, int intervalDays, BigDecimal easeFactor) {
        this.memberId = memberId;
        this.flashcardId = flashcardId;
        this.sequence = sequence;
        this.scheduledAt = scheduledAt;
        this.intervalDays = intervalDays;
        this.easeFactor = easeFactor;
        this.status = ReviewStatus.PENDING;
    }

    /**
     * 첫 복습 일정을 생성한다. due 시각은 사용자 시간대 기준 익일 04:00(롤오버)을 UTC로 환산한 값이다.
     */
    public static ReviewSchedule firstReview(Long memberId, Long flashcardId, LocalDate today, ZoneId zone) {
        Instant scheduledAt = today.plusDays(1).atTime(ROLLOVER_HOUR, 0).atZone(zone).toInstant();
        return new ReviewSchedule(memberId, flashcardId, 1, scheduledAt, 1, INITIAL_EASE_FACTOR);
    }

    /**
     * 다음 복습 due 시각을 계산한다. 저장은 UTC({@link Instant})지만 "당일/익일 04:00 롤오버"는
     * 사용자 시간대({@code zone}) 기준으로 계산한다.
     * AGAIN(틀림)은 {@value #RELEARN_MINUTES}분 뒤(분 단위), 그 외는 사용자 로컬 날짜 기준 일 간격 뒤 04:00.
     */
    public static Instant computeScheduledAt(Rating rating, int intervalDays, Instant now, ZoneId zone) {
        if (rating == Rating.AGAIN) {
            return now.plusSeconds(RELEARN_MINUTES * 60L);
        }
        LocalDate dueDate = now.atZone(zone).toLocalDate().plusDays(intervalDays);
        return dueDate.atTime(ROLLOVER_HOUR, 0).atZone(zone).toInstant();
    }

    /**
     * 복습을 완료 처리한다. 경과일(elapsedDays)은 사용자 시간대 기준 날짜 차이로 계산한다.
     */
    public void complete(Rating rating, Instant now, ZoneId zone) {
        this.status = ReviewStatus.COMPLETED;
        this.rating = rating;
        this.elapsedDays = (int) ChronoUnit.DAYS.between(
                this.scheduledAt.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate());
        this.completedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getFlashcardId() {
        return flashcardId;
    }

    public Integer getSequence() {
        return sequence;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
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

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
