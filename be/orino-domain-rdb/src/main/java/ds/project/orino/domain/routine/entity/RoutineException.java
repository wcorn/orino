package ds.project.orino.domain.routine.entity;

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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_routine_exception_routine_date",
        columnNames = {"routine_id", "exception_date"}))
@EntityListeners(AuditingEntityListener.class)
public class RoutineException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id", nullable = false)
    private Routine routine;

    @Column(nullable = false)
    private LocalDate exceptionDate;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RoutineException() {
    }

    public RoutineException(Routine routine, LocalDate exceptionDate) {
        this.routine = routine;
        this.exceptionDate = exceptionDate;
    }

    public Long getId() {
        return id;
    }

    public Routine getRoutine() {
        return routine;
    }

    public LocalDate getExceptionDate() {
        return exceptionDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
