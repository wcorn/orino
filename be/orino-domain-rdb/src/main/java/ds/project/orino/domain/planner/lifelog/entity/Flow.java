package ds.project.orino.domain.planner.lifelog.entity;

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

import java.time.Instant;

/**
 * 흐름(수동 컬렉션). 사용자가 "제주 여행"처럼 만들고 기록을 담는다. 담긴 기록은 {@link FlowMoment}로
 * N:M 연결되며, 흐름은 관점일 뿐 기록의 소유자가 아니다 — 흐름을 지워도 기록은 남는다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "flow")
public class Flow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 커버 이미지 key. 비우면 담긴 첫 사진으로 대체 표시(조회 시 결정). */
    @Column(name = "cover_object_key", length = 512)
    private String coverObjectKey;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlowStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Flow() {
    }

    public Flow(Long memberId, String title, String description) {
        this.memberId = memberId;
        this.title = title;
        this.description = description;
        this.status = FlowStatus.ACTIVE;
    }

    public void update(String title, String description, String coverObjectKey,
                       Instant startedAt, Instant endedAt, FlowStatus status) {
        this.title = title;
        this.description = description;
        this.coverObjectKey = coverObjectKey;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.status = status;
    }

    /** 담긴 기록에서 유도한 기간(min/max occurred_at)을 반영한다. */
    public void updatePeriod(Instant startedAt, Instant endedAt) {
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getCoverObjectKey() {
        return coverObjectKey;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public FlowStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
