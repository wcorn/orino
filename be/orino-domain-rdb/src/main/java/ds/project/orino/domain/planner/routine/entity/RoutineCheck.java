package ds.project.orino.domain.planner.routine.entity;

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
 * 습관 루틴의 날짜별 완료 오버레이. 행의 존재 = 완료(체크), 부재 = 미완료.
 *
 * <p>Google이 진실 원천인 루틴 시리즈 위에 orino가 얹는 유일한 신규 테이블. 완료 boolean을 따로 두지 않고
 * {@code (member_id, recurring_event_id, instance_date)} UNIQUE 행의 유무로 상태를 표현한다.
 */
@Entity
@Table(
        name = "routine_check",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_routine_check_instance",
                columnNames = {"member_id", "recurring_event_id", "instance_date"}))
@EntityListeners(AuditingEntityListener.class)
public class RoutineCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** Google 반복 이벤트의 recurringEventId(시리즈 안정 키). */
    @Column(name = "recurring_event_id", nullable = false, length = 255)
    private String recurringEventId;

    /** 완료한 인스턴스의 날짜(사용자 시간대 로컬 날짜). */
    @Column(name = "instance_date", nullable = false)
    private LocalDate instanceDate;

    /** 체크 시각(UTC). */
    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected RoutineCheck() {
    }

    public RoutineCheck(Long memberId, String recurringEventId, LocalDate instanceDate) {
        this.memberId = memberId;
        this.recurringEventId = recurringEventId;
        this.instanceDate = instanceDate;
        this.checkedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getRecurringEventId() {
        return recurringEventId;
    }

    public LocalDate getInstanceDate() {
        return instanceDate;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
