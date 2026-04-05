package ds.project.orino.domain.reflection.entity;

import ds.project.orino.domain.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_reflection",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_reflection_member_date",
                columnNames = {"member_id", "reflection_date"}))
@EntityListeners(AuditingEntityListener.class)
public class DailyReflection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "reflection_date", nullable = false)
    private LocalDate reflectionDate;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(nullable = false)
    private int mood;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected DailyReflection() {
    }

    public DailyReflection(Member member, LocalDate reflectionDate,
                           int mood, String memo) {
        this.member = member;
        this.reflectionDate = reflectionDate;
        this.mood = mood;
        this.memo = memo;
    }

    public void update(int mood, String memo) {
        this.mood = mood;
        this.memo = memo;
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public LocalDate getReflectionDate() {
        return reflectionDate;
    }

    public String getMemo() {
        return memo;
    }

    public int getMood() {
        return mood;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
