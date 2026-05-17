package ds.project.orino.domain.planner.note.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "note", uniqueConstraints = @UniqueConstraint(name = "uk_note_material", columnNames = "material_id"))
public class Note {

    public static final String DEFAULT_CONTENT = "{\"type\":\"doc\",\"content\":[]}";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(nullable = false, columnDefinition = "JSON")
    private String content;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Note() {
    }

    public Note(Long memberId, Long materialId) {
        this(memberId, materialId, DEFAULT_CONTENT);
    }

    public Note(Long memberId, Long materialId, String content) {
        this.memberId = memberId;
        this.materialId = materialId;
        this.content = content;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
