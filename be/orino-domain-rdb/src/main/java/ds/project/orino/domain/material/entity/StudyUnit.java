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
import jakarta.persistence.ManyToOne;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class StudyUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private StudyMaterial material;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private int estimatedMinutes = 30;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private UnitDifficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private UnitStatus status = UnitStatus.PENDING;

    private LocalDateTime completedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected StudyUnit() {
    }

    public StudyUnit(StudyMaterial material, String title,
                     int sortOrder, Integer estimatedMinutes,
                     UnitDifficulty difficulty) {
        this.material = material;
        this.title = title;
        this.sortOrder = sortOrder;
        this.estimatedMinutes = estimatedMinutes != null
                ? estimatedMinutes : 30;
        this.difficulty = difficulty;
    }

    public void update(String title, int sortOrder,
                       Integer estimatedMinutes,
                       UnitDifficulty difficulty) {
        this.title = title;
        this.sortOrder = sortOrder;
        this.estimatedMinutes = estimatedMinutes != null
                ? estimatedMinutes : 30;
        this.difficulty = difficulty;
    }

    public Long getId() {
        return id;
    }

    public StudyMaterial getMaterial() {
        return material;
    }

    public String getTitle() {
        return title;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public UnitDifficulty getDifficulty() {
        return difficulty;
    }

    public UnitStatus getStatus() {
        return status;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
