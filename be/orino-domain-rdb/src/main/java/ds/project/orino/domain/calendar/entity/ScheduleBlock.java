package ds.project.orino.domain.calendar.entity;

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

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "schedule_block")
@EntityListeners(AuditingEntityListener.class)
public class ScheduleBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_schedule_id", nullable = false)
    private DailySchedule dailySchedule;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", length = 15, nullable = false)
    private BlockType blockType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private BlockStatus status = BlockStatus.SCHEDULED;

    @Column(name = "is_pinned", nullable = false)
    private boolean pinned;

    private LocalDateTime completedAt;

    @Column(nullable = false)
    private int sortOrder;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected ScheduleBlock() {
    }

    public ScheduleBlock(DailySchedule dailySchedule, BlockType blockType,
                         Long referenceId, LocalTime startTime,
                         LocalTime endTime, int sortOrder) {
        this.dailySchedule = dailySchedule;
        this.blockType = blockType;
        this.referenceId = referenceId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.sortOrder = sortOrder;
    }

    public void complete() {
        this.status = BlockStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void postpone() {
        this.status = BlockStatus.POSTPONED;
    }

    public void reschedule(LocalTime startTime, LocalTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.pinned = true;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isLocked() {
        return status == BlockStatus.COMPLETED
                || status == BlockStatus.POSTPONED
                || pinned;
    }

    public Long getId() {
        return id;
    }

    public DailySchedule getDailySchedule() {
        return dailySchedule;
    }

    public BlockType getBlockType() {
        return blockType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public BlockStatus getStatus() {
        return status;
    }

    public boolean isPinned() {
        return pinned;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
