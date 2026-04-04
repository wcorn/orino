package ds.project.orino.domain.preference.entity;

import ds.project.orino.domain.material.entity.MissedPolicy;
import ds.project.orino.domain.member.entity.Member;
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
import jakarta.persistence.OneToOne;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @Column(nullable = false)
    private LocalTime wakeTime = LocalTime.of(7, 0);

    @Column(nullable = false)
    private LocalTime sleepTime = LocalTime.MIDNIGHT;

    @Column(nullable = false)
    private int dailyStudyMinutes = 240;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private StudyTimePreference studyTimePreference =
            StudyTimePreference.MORNING;

    @Column(nullable = false)
    private int focusMinutes = 50;

    @Column(nullable = false)
    private int breakMinutes = 10;

    @Column(length = 30)
    private String restDays;

    @Column(nullable = false)
    private boolean skipHolidays;

    @Column(length = 50, nullable = false)
    private String defaultReviewIntervals = "1,2,3,7,15,30";

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private MissedPolicy defaultMissedPolicy = MissedPolicy.IMMEDIATE;

    @Column(nullable = false)
    private int streakFreezePerMonth = 2;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected UserPreference() {
    }

    public UserPreference(Member member) {
        this.member = member;
    }

    public void update(LocalTime wakeTime, LocalTime sleepTime,
                       int dailyStudyMinutes,
                       StudyTimePreference studyTimePreference,
                       int focusMinutes, int breakMinutes,
                       String restDays, Boolean skipHolidays,
                       String defaultReviewIntervals,
                       MissedPolicy defaultMissedPolicy,
                       int streakFreezePerMonth) {
        this.wakeTime = wakeTime;
        this.sleepTime = sleepTime;
        this.dailyStudyMinutes = dailyStudyMinutes;
        this.studyTimePreference = studyTimePreference;
        this.focusMinutes = focusMinutes;
        this.breakMinutes = breakMinutes;
        this.restDays = restDays;
        this.skipHolidays = Boolean.TRUE.equals(skipHolidays);
        this.defaultReviewIntervals = defaultReviewIntervals;
        this.defaultMissedPolicy = defaultMissedPolicy;
        this.streakFreezePerMonth = streakFreezePerMonth;
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public LocalTime getWakeTime() {
        return wakeTime;
    }

    public LocalTime getSleepTime() {
        return sleepTime;
    }

    public int getDailyStudyMinutes() {
        return dailyStudyMinutes;
    }

    public StudyTimePreference getStudyTimePreference() {
        return studyTimePreference;
    }

    public int getFocusMinutes() {
        return focusMinutes;
    }

    public int getBreakMinutes() {
        return breakMinutes;
    }

    public String getRestDays() {
        return restDays;
    }

    public boolean isSkipHolidays() {
        return skipHolidays;
    }

    public String getDefaultReviewIntervals() {
        return defaultReviewIntervals;
    }

    public MissedPolicy getDefaultMissedPolicy() {
        return defaultMissedPolicy;
    }

    public int getStreakFreezePerMonth() {
        return streakFreezePerMonth;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
