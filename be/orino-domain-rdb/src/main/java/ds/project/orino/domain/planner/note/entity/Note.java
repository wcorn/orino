package ds.project.orino.domain.planner.note.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "note")
public class Note {

    public static final String DEFAULT_CONTENT = "{\"type\":\"doc\",\"content\":[]}";
    public static final String DEFAULT_TITLE = "제목 없음";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 학습자료 종속 노트는 자료 id, 독립 노트는 null. */
    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(nullable = false, columnDefinition = "JSON")
    private String content;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected Note() {
    }

    /**
     * 자료의 루트 노트(빈 doc)를 만든다. parentId=null, 기본 제목, sortOrder=0.
     */
    public Note(Long memberId, Long materialId) {
        this(memberId, materialId, null, DEFAULT_TITLE, 0, DEFAULT_CONTENT);
    }

    public Note(Long memberId, Long materialId, Long parentId, String title, int sortOrder) {
        this(memberId, materialId, parentId, title, sortOrder, DEFAULT_CONTENT);
    }

    public Note(Long memberId, Long materialId, Long parentId, String title,
                int sortOrder, String content) {
        this.memberId = memberId;
        this.materialId = materialId;
        this.parentId = parentId;
        this.title = (title == null || title.isBlank()) ? DEFAULT_TITLE : title;
        this.sortOrder = sortOrder;
        this.content = content;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    public void updateParent(Long parentId) {
        this.parentId = parentId;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
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

    public Long getParentId() {
        return parentId;
    }

    public String getTitle() {
        return title;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
