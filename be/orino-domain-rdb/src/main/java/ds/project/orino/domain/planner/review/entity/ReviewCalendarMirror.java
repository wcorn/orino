package ds.project.orino.domain.planner.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 복습 묶음 ↔ Google 보조 캘린더 종일 이벤트 매핑. dueDate 하루치 PENDING 복습을 "복습 N개" 종일 이벤트
 * 1개로 단방향 미러한다(orino=진실, Google=투영).
 *
 * <p>{@code (member_id, due_date)} UNIQUE로 dueDate당 1 row를 보장해 멱등 upsert의 키가 된다.
 * {@code google_event_id}는 Google이 삭제됐을 때 self-heal로 재생성되며 갱신될 수 있다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "review_calendar_mirror", uniqueConstraints = @UniqueConstraint(
        name = "uk_review_calendar_mirror_member_date", columnNames = {"member_id", "due_date"}))
public class ReviewCalendarMirror {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "google_event_id", nullable = false, length = 255)
    private String googleEventId;

    @Column(name = "pending_count", nullable = false)
    private int pendingCount;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected ReviewCalendarMirror() {
    }

    public ReviewCalendarMirror(Long memberId, LocalDate dueDate, String googleEventId,
                                int pendingCount, Instant syncedAt) {
        this.memberId = memberId;
        this.dueDate = dueDate;
        this.googleEventId = googleEventId;
        this.pendingCount = pendingCount;
        this.syncedAt = syncedAt;
    }

    /**
     * 동기화 결과를 반영한다. Google 이벤트가 그대로면 동일 {@code googleEventId}로, self-heal로 재생성됐으면
     * 새 id로 갱신한다.
     */
    public void sync(String googleEventId, int pendingCount, Instant syncedAt) {
        this.googleEventId = googleEventId;
        this.pendingCount = pendingCount;
        this.syncedAt = syncedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getGoogleEventId() {
        return googleEventId;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
