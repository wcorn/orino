package ds.project.orino.domain.calendar.entity;

import ds.project.orino.domain.member.entity.Member;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "daily_schedule",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_schedule_member_date",
                columnNames = {"member_id", "schedule_date"}))
@EntityListeners(AuditingEntityListener.class)
public class DailySchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "schedule_date", nullable = false)
    private LocalDate scheduleDate;

    @Column(name = "is_dirty", nullable = false)
    private boolean dirty = true;

    private LocalDateTime generatedAt;

    @Column(nullable = false)
    private int totalBlocks;

    @Column(nullable = false)
    private int completedBlocks;

    @OneToMany(mappedBy = "dailySchedule",
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScheduleBlock> blocks = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected DailySchedule() {
    }

    public DailySchedule(Member member, LocalDate scheduleDate) {
        this.member = member;
        this.scheduleDate = scheduleDate;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void markGenerated(int totalBlocks, int completedBlocks) {
        this.dirty = false;
        this.totalBlocks = totalBlocks;
        this.completedBlocks = completedBlocks;
        this.generatedAt = LocalDateTime.now();
    }

    public void addBlock(ScheduleBlock block) {
        blocks.add(block);
    }

    public void removeBlocks(List<ScheduleBlock> toRemove) {
        blocks.removeAll(toRemove);
    }

    public void clearBlocks() {
        blocks.clear();
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public LocalDate getScheduleDate() {
        return scheduleDate;
    }

    public boolean isDirty() {
        return dirty;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public int getCompletedBlocks() {
        return completedBlocks;
    }

    public List<ScheduleBlock> getBlocks() {
        return blocks;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
