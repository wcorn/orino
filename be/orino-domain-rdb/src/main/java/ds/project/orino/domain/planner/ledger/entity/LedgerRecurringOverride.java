package ds.project.orino.domain.planner.ledger.entity;

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
import java.time.LocalDate;

/**
 * 사람이 손댄 회차 <b>하나</b>. 건드리지 않은 회차는 행이 없다.
 *
 * <p>이 표가 D-5의 하이브리드를 성립시킨다 — 규칙 수정은 파생이라 즉시 반영되고(LDG-055),
 * 단건 수정은 여기 1행으로 남는다(LDG-056).
 *
 * <p>{@code occurrenceDate}는 <b>규칙이 계산한 원래 예정일</b>이다. 날짜를 옮겨도
 * ({@link LedgerOverrideAction#MOVE}) 이 값은 그대로이고 실제 날짜는 {@code movedTo}에
 * 들어간다. 보정·이동 후 날짜를 키로 삼으면 같은 회차가 두 회차로 갈라진다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ledger_recurring_override")
public class LedgerRecurringOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recurring_id", nullable = false)
    private Long recurringId;

    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerOverrideAction action;

    @Column
    private Long amount;

    /** 옮긴 날짜 또는 미납이 실제로 빠진 날. */
    @Column(name = "moved_to")
    private LocalDate movedTo;

    @Column(length = 200)
    private String note;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerRecurringOverride() {
    }

    public LedgerRecurringOverride(Long recurringId, LocalDate occurrenceDate,
                                   LedgerOverrideAction action) {
        this.recurringId = recurringId;
        this.occurrenceDate = occurrenceDate;
        this.action = action;
    }

    public void apply(LedgerOverrideAction action, Long amount, LocalDate movedTo, String note) {
        this.action = action;
        this.amount = amount;
        this.movedTo = movedTo;
        this.note = note;
    }

    /** 미납이 실제로 빠졌다. 미납 표시를 걷고 그날로 옮긴 회차가 된다. */
    public void confirmAt(LocalDate actualDate, Long amount) {
        this.action = LedgerOverrideAction.MOVE;
        this.movedTo = actualDate;
        if (amount != null) {
            this.amount = amount;
        }
    }

    /** 그 회차가 실제로 잡히는 날. 옮기지 않았으면 원래 예정일이다. */
    public LocalDate effectiveDate() {
        return movedTo != null ? movedTo : occurrenceDate;
    }

    public Long getId() {
        return id;
    }

    public Long getRecurringId() {
        return recurringId;
    }

    public LocalDate getOccurrenceDate() {
        return occurrenceDate;
    }

    public LedgerOverrideAction getAction() {
        return action;
    }

    public Long getAmount() {
        return amount;
    }

    public LocalDate getMovedTo() {
        return movedTo;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
