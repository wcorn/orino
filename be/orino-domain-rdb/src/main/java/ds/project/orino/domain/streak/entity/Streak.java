package ds.project.orino.domain.streak.entity;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.routine.entity.Routine;
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
@Table(name = "streak")
@EntityListeners(AuditingEntityListener.class)
public class Streak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "streak_type", length = 10, nullable = false)
    private StreakType streakType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id")
    private Routine routine;

    @Column(name = "current_count", nullable = false)
    private int currentCount;

    @Column(name = "longest_count", nullable = false)
    private int longestCount;

    @Column(name = "last_achieved_date")
    private LocalDate lastAchievedDate;

    @Column(name = "freeze_used_this_month", nullable = false)
    private int freezeUsedThisMonth;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Streak() {
    }

    public Streak(Member member, StreakType streakType, Routine routine) {
        this.member = member;
        this.streakType = streakType;
        this.routine = routine;
    }

    public static Streak overall(Member member) {
        return new Streak(member, StreakType.OVERALL, null);
    }

    public static Streak routine(Member member, Routine routine) {
        return new Streak(member, StreakType.ROUTINE, routine);
    }

    public void increment(LocalDate achievedDate) {
        this.currentCount += 1;
        if (this.currentCount > this.longestCount) {
            this.longestCount = this.currentCount;
        }
        this.lastAchievedDate = achievedDate;
    }

    public void reset() {
        this.currentCount = 0;
    }

    public void useFreeze() {
        this.freezeUsedThisMonth += 1;
    }

    public void resetMonthlyFreeze() {
        this.freezeUsedThisMonth = 0;
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public StreakType getStreakType() {
        return streakType;
    }

    public Routine getRoutine() {
        return routine;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public int getLongestCount() {
        return longestCount;
    }

    public LocalDate getLastAchievedDate() {
        return lastAchievedDate;
    }

    public int getFreezeUsedThisMonth() {
        return freezeUsedThisMonth;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
