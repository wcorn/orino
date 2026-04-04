package ds.project.orino.domain.fixedschedule.entity;

import ds.project.orino.domain.category.entity.Category;
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
import jakarta.persistence.ManyToOne;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class FixedSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(length = 100, nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    private LocalDate scheduleDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private RecurrenceType recurrenceType = RecurrenceType.NONE;

    private Integer recurrenceInterval;

    @Column(length = 50)
    private String recurrenceDays;

    private LocalDate recurrenceStart;

    private LocalDate recurrenceEnd;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected FixedSchedule() {
    }

    public FixedSchedule(Member member, String title, Category category,
                         LocalTime startTime, LocalTime endTime,
                         LocalDate scheduleDate, RecurrenceType recurrenceType,
                         Integer recurrenceInterval, String recurrenceDays,
                         LocalDate recurrenceStart, LocalDate recurrenceEnd) {
        this.member = member;
        this.title = title;
        this.category = category;
        this.startTime = startTime;
        this.endTime = endTime;
        this.scheduleDate = scheduleDate;
        this.recurrenceType = recurrenceType;
        this.recurrenceInterval = recurrenceInterval;
        this.recurrenceDays = recurrenceDays;
        this.recurrenceStart = recurrenceStart;
        this.recurrenceEnd = recurrenceEnd;
    }

    public void update(String title, Category category,
                       LocalTime startTime, LocalTime endTime,
                       LocalDate scheduleDate, RecurrenceType recurrenceType,
                       Integer recurrenceInterval, String recurrenceDays,
                       LocalDate recurrenceStart, LocalDate recurrenceEnd) {
        this.title = title;
        this.category = category;
        this.startTime = startTime;
        this.endTime = endTime;
        this.scheduleDate = scheduleDate;
        this.recurrenceType = recurrenceType;
        this.recurrenceInterval = recurrenceInterval;
        this.recurrenceDays = recurrenceDays;
        this.recurrenceStart = recurrenceStart;
        this.recurrenceEnd = recurrenceEnd;
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public String getTitle() {
        return title;
    }

    public Category getCategory() {
        return category;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public LocalDate getScheduleDate() {
        return scheduleDate;
    }

    public RecurrenceType getRecurrenceType() {
        return recurrenceType;
    }

    public Integer getRecurrenceInterval() {
        return recurrenceInterval;
    }

    public String getRecurrenceDays() {
        return recurrenceDays;
    }

    public LocalDate getRecurrenceStart() {
        return recurrenceStart;
    }

    public LocalDate getRecurrenceEnd() {
        return recurrenceEnd;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
