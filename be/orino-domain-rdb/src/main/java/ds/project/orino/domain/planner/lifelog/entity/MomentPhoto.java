package ds.project.orino.domain.planner.lifelog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 기록에 붙은 사진 하나. 바이트는 self-hosted MinIO에 있고 여기엔 object key만 담는다.
 * 공개 URL은 base(img.orino.dev) + key로 조립한다(호스트 하드코딩 회피).
 *
 * <p>EXIF(촬영시각·GPS)는 발생시각·위치 자동채움의 근거로 원본을 보존한다 —
 * {@link Moment}의 값은 사용자가 수정할 수 있으므로 사진 EXIF와 별개다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "moment_photo")
public class MomentPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "moment_id", nullable = false)
    private Long momentId;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "thumb_key", length = 512)
    private String thumbKey;

    private Integer width;

    private Integer height;

    @Column(name = "exif_taken_at")
    private Instant exifTakenAt;

    @Column(name = "exif_lat", precision = 10, scale = 7)
    private BigDecimal exifLat;

    @Column(name = "exif_lng", precision = 10, scale = 7)
    private BigDecimal exifLng;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MomentPhoto() {
    }

    public MomentPhoto(Long momentId, String objectKey, String thumbKey,
                       Integer width, Integer height,
                       Instant exifTakenAt, BigDecimal exifLat, BigDecimal exifLng,
                       int sortOrder) {
        this.momentId = momentId;
        this.objectKey = objectKey;
        this.thumbKey = thumbKey;
        this.width = width;
        this.height = height;
        this.exifTakenAt = exifTakenAt;
        this.exifLat = exifLat;
        this.exifLng = exifLng;
        this.sortOrder = sortOrder;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getMomentId() {
        return momentId;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getThumbKey() {
        return thumbKey;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public Instant getExifTakenAt() {
        return exifTakenAt;
    }

    public BigDecimal getExifLat() {
        return exifLat;
    }

    public BigDecimal getExifLng() {
        return exifLng;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
