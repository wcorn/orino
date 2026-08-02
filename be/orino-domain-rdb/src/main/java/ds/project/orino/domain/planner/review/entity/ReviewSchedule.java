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
import ds.project.orino.common.time.StudyDay;
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

    /**
     * 다중일 복습 due 시각(롤오버). 해당 날짜 04:00부터 due가 되어 그 날 하루 종일 복습 가능.
     * 학습일 경계와 같은 값이다 — 단일 출처는 {@link StudyDay#ROLLOVER_HOUR}.
     */
    public static final int ROLLOVER_HOUR = StudyDay.ROLLOVER_HOUR;

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
        return firstReview(memberId, flashcardId, today, zone, 1);
    }

    /**
     * 첫 복습 일정을 {@code afterDays}일 뒤 04:00으로 생성한다. 양방향 짝 카드의 첫 복습 엇갈림
     * (A=today+1, B=today+2)에 사용한다. {@code interval_days}는 1로 동일하다.
     */
    public static ReviewSchedule firstReview(Long memberId, Long flashcardId, LocalDate today,
                                             ZoneId zone, int afterDays) {
        Instant scheduledAt = StudyDay.startOf(today.plusDays(afterDays), zone);
        return new ReviewSchedule(memberId, flashcardId, 1, scheduledAt, 1, INITIAL_EASE_FACTOR);
    }

    /**
     * 다음 복습 due 시각을 계산한다. 저장은 UTC({@link Instant})지만 "며칠 뒤 04:00"은
     * 사용자 시간대({@code zone}) 기준 <b>학습일</b>({@link StudyDay})로 센다 — 새벽 1시에 본 복습은
     * 아직 전날 몫이라 다음 복습도 하루 앞에서 출발한다(#1003).
     * AGAIN(틀림)은 {@value #RELEARN_MINUTES}분 뒤(분 단위), 그 외는 학습일 기준 일 간격 뒤 04:00.
     */
    public static Instant computeScheduledAt(Rating rating, int intervalDays, Instant now, ZoneId zone) {
        if (rating == Rating.AGAIN) {
            return now.plusSeconds(RELEARN_MINUTES * 60L);
        }
        return StudyDay.startOf(StudyDay.of(now, zone).plusDays(intervalDays), zone);
    }

    /**
     * 복습을 완료 처리한다. 경과일(elapsedDays)은 학습일 기준 날짜 차이로 계산한다.
     */
    public void complete(Rating rating, Instant now, ZoneId zone) {
        this.status = ReviewStatus.COMPLETED;
        this.rating = rating;
        this.elapsedDays = (int) ChronoUnit.DAYS.between(
                StudyDay.of(this.scheduledAt, zone), StudyDay.of(now, zone));
        this.completedAt = now;
    }

    /**
     * 간격 규칙이 바뀌었을 때(#1001) 아직 오지 않은 복습을 다시 계산해 덮어쓴다.
     * 회차·상태·평가 기록은 그대로 두고 간격·ease·due 시각만 바꾼다.
     */
    public void reschedule(int intervalDays, BigDecimal easeFactor, Instant scheduledAt) {
        this.intervalDays = intervalDays;
        this.easeFactor = easeFactor;
        this.scheduledAt = scheduledAt;
    }

    /**
     * Sibling burying — due 시각을 익일 04:00(사용자 시간대)으로 미룬다.
     * SM-2 간격/ease/sequence/status는 건드리지 않고 due 날짜만 이동한다.
     */
    public void bury(Instant now, ZoneId zone) {
        this.scheduledAt = StudyDay.startOf(StudyDay.of(now, zone).plusDays(1), zone);
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
