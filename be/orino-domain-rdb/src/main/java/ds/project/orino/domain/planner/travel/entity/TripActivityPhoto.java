package ds.project.orino.domain.planner.travel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 기록에 붙은 사진 하나. 바이트는 MinIO에 있고 여기엔 object key만 담는다 - 공개 URL은
 * 설정의 endpoint + bucket + key로 조립한다(호스트 하드코딩 회피).
 *
 * <p><b>위치정보를 담지 않는다</b>(§1.6). 여행 사진의 EXIF는 FE canvas 재인코딩으로 이미
 * 제거된 상태로 올라온다 - 쓰지 않을 위치정보를 받아 두지 않는다. (일상기록 사진은 시각·위치
 * 자동채움에 쓰려고 EXIF를 보존하는데, 여행은 그 요구가 없다.)
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "trip_activity_photo")
public class TripActivityPhoto {

    /** 기록 한 건에 붙일 수 있는 사진 수. 화면·검증·문서가 같은 값을 봐야 한다. */
    public static final int MAX_PHOTOS = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 일정이 아니라 <b>기록</b>에 매달린다 - 기록이 지워지면 사진도 사라진다(FK CASCADE). */
    @Column(name = "log_id", nullable = false)
    private Long logId;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    /** 썸네일 업로드만 실패할 수 있다. null이면 화면이 원본을 줄여 보여준다. */
    @Column(name = "thumb_key", length = 512)
    private String thumbKey;

    private Integer width;

    private Integer height;

    /** 업로드 순서. 찍은 순서가 곧 보는 순서다. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TripActivityPhoto() {
    }

    public TripActivityPhoto(Long logId, String objectKey, String thumbKey,
                             Integer width, Integer height, int sortOrder) {
        this.logId = logId;
        this.objectKey = objectKey;
        this.thumbKey = thumbKey;
        this.width = width;
        this.height = height;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getLogId() {
        return logId;
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

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
