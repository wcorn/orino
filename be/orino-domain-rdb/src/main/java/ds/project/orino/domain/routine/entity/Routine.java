package ds.project.orino.domain.routine.entity;

import ds.project.orino.domain.category.entity.Category;
import ds.project.orino.domain.fixedschedule.entity.RecurrenceType;
import ds.project.orino.domain.member.entity.Member;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class Routine {

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
    private int durationMinutes;

    private LocalTime preferredTime;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private RecurrenceType recurrenceType;

    private Integer recurrenceInterval;

    @Column(length = 50)
    private String recurrenceDays;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @Column(nullable = false)
    private boolean skipHolidays;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private RoutineStatus status = RoutineStatus.ACTIVE;

    @OneToMany(mappedBy = "routine", cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<RoutineCheck> checks = new ArrayList<>();

    @OneToMany(mappedBy = "routine", cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<RoutineException> exceptions = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Routine() {
    }

    public Routine(Member member, String title, Category category,
                   int durationMinutes, LocalTime preferredTime,
                   RecurrenceType recurrenceType, Integer recurrenceInterval,
                   String recurrenceDays, LocalDate startDate, LocalDate endDate,
                   boolean skipHolidays) {
        this.member = member;
        this.title = title;
        this.category = category;
        this.durationMinutes = durationMinutes;
        this.preferredTime = preferredTime;
        this.recurrenceType = recurrenceType;
        this.recurrenceInterval = recurrenceInterval;
        this.recurrenceDays = recurrenceDays;
        this.startDate = startDate;
        this.endDate = endDate;
        this.skipHolidays = skipHolidays;
    }

    public void update(String title, Category category,
                       int durationMinutes, LocalTime preferredTime,
                       RecurrenceType recurrenceType,
                       Integer recurrenceInterval, String recurrenceDays,
                       LocalDate startDate, LocalDate endDate,
                       boolean skipHolidays) {
        this.title = title;
        this.category = category;
        this.durationMinutes = durationMinutes;
        this.preferredTime = preferredTime;
        this.recurrenceType = recurrenceType;
        this.recurrenceInterval = recurrenceInterval;
        this.recurrenceDays = recurrenceDays;
        this.startDate = startDate;
        this.endDate = endDate;
        this.skipHolidays = skipHolidays;
    }

    public void changeStatus(RoutineStatus status) {
        this.status = status;
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

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public LocalTime getPreferredTime() {
        return preferredTime;
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

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isSkipHolidays() {
        return skipHolidays;
    }

    public RoutineStatus getStatus() {
        return status;
    }

    public List<RoutineCheck> getChecks() {
        return checks;
    }

    public List<RoutineException> getExceptions() {
        return exceptions;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
