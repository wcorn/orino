package ds.project.orino.domain.material.entity;

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

@Entity
@EntityListeners(AuditingEntityListener.class)
public class ReviewConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false, unique = true)
    private StudyMaterial material;

    @Column(length = 50, nullable = false)
    private String intervals;

    @Enumerated(EnumType.STRING)
    @Column(name = "missed_policy", length = 10, nullable = false)
    private MissedPolicy missedPolicy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected ReviewConfig() {
    }

    public ReviewConfig(StudyMaterial material, String intervals,
                        MissedPolicy missedPolicy) {
        this.material = material;
        this.intervals = intervals;
        this.missedPolicy = missedPolicy;
    }

    public void update(String intervals, MissedPolicy missedPolicy) {
        this.intervals = intervals;
        this.missedPolicy = missedPolicy;
    }

    public Long getId() {
        return id;
    }

    public StudyMaterial getMaterial() {
        return material;
    }

    public String getIntervals() {
        return intervals;
    }

    public MissedPolicy getMissedPolicy() {
        return missedPolicy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
