package ds.project.orino.domain.planner.flashcard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "flashcard")
public class Flashcard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FlashcardType type;

    @Column(nullable = false, length = 1000)
    private String front;

    /** BASIC 카드의 뒷면. ORDERING 카드는 null. */
    @Column(length = 1000)
    private String back;

    /** ORDERING 카드의 항목 배열(정답 순서). BASIC 카드는 null. {@code [{"id","text"}]} JSON 문자열. */
    @Column(columnDefinition = "JSON")
    private String items;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected Flashcard() {
    }

    /** BASIC 카드를 생성한다. */
    public Flashcard(Long memberId, Long materialId, String front, String back) {
        this(memberId, materialId, FlashcardType.BASIC, front, back, null);
    }

    private Flashcard(Long memberId, Long materialId, FlashcardType type,
                      String front, String back, String items) {
        this.memberId = memberId;
        this.materialId = materialId;
        this.type = type;
        this.front = front;
        this.back = back;
        this.items = items;
    }

    /** ORDERING 카드를 생성한다. {@code items}는 정답 순서로 직렬화된 JSON 문자열. */
    public static Flashcard ordering(Long memberId, Long materialId, String front, String items) {
        return new Flashcard(memberId, materialId, FlashcardType.ORDERING, front, null, items);
    }

    public void updateFront(String front) {
        this.front = front;
    }

    /** BASIC 카드로 전환/갱신한다. items는 비운다. */
    public void changeToBasic(String back) {
        this.type = FlashcardType.BASIC;
        this.back = back;
        this.items = null;
    }

    /** ORDERING 카드로 전환/갱신한다. back은 비운다. {@code items}는 정답 순서 JSON 문자열. */
    public void changeToOrdering(String items) {
        this.type = FlashcardType.ORDERING;
        this.items = items;
        this.back = null;
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

    public FlashcardType getType() {
        return type;
    }

    public String getFront() {
        return front;
    }

    public String getBack() {
        return back;
    }

    public String getItems() {
        return items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
