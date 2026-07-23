package ds.project.orino.domain.planner.lifelog.entity;

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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 순간 기록 하나. 사진·본문·발생시각·위치·기분을 담는 리치 카드의 본체.
 *
 * <p>사진(0..N)·태그(0..N)는 {@link MomentPhoto}·{@link MomentTag}에 별도로 담고, 흐름 소속은
 * {@link FlowMoment}가 N:M으로 잇는다. 위치(lat/lng)와 장소명은 조회마다 재계산하지 않도록
 * 저장 시 확정해 여기 denormalize한다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "moment")
public class Moment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 그 순간이 실제로 일어난 시각(정렬 키). 사진 EXIF 촬영시각 우선 → 없으면 작성시각. */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Mood mood;

    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    /** 역지오코딩 결과. 저장 시 1회 확정(denormalize). */
    @Column(name = "place_name", length = 255)
    private String placeName;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Moment() {
    }

    public Moment(Long memberId, Instant occurredAt, String body, Mood mood,
                  BigDecimal lat, BigDecimal lng, String placeName) {
        this.memberId = memberId;
        this.occurredAt = occurredAt;
        this.body = body;
        this.mood = mood;
        this.lat = lat;
        this.lng = lng;
        this.placeName = placeName;
    }

    /**
     * 본문·발생시각·기분·위치를 한 번에 갱신한다. 사진·태그는 별도 컬렉션이라 여기서 다루지 않는다.
     */
    public void update(Instant occurredAt, String body, Mood mood,
                       BigDecimal lat, BigDecimal lng, String placeName) {
        this.occurredAt = occurredAt;
        this.body = body;
        this.mood = mood;
        this.lat = lat;
        this.lng = lng;
        this.placeName = placeName;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getBody() {
        return body;
    }

    public Mood getMood() {
        return mood;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public BigDecimal getLng() {
        return lng;
    }

    public String getPlaceName() {
        return placeName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
