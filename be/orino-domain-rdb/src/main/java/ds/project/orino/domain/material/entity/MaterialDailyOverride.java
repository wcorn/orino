package ds.project.orino.domain.material.entity;

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
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_material_daily_override_material_date",
        columnNames = {"material_id", "override_date"}))
@EntityListeners(AuditingEntityListener.class)
public class MaterialDailyOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private StudyMaterial material;

    @Column(name = "override_date", nullable = false)
    private LocalDate overrideDate;

    @Column(nullable = false)
    private int minutes;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected MaterialDailyOverride() {
    }

    public MaterialDailyOverride(StudyMaterial material,
                                 LocalDate overrideDate, int minutes) {
        this.material = material;
        this.overrideDate = overrideDate;
        this.minutes = minutes;
    }

    public void update(int minutes) {
        this.minutes = minutes;
    }

    public Long getId() {
        return id;
    }

    public StudyMaterial getMaterial() {
        return material;
    }

    public LocalDate getOverrideDate() {
        return overrideDate;
    }

    public int getMinutes() {
        return minutes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
