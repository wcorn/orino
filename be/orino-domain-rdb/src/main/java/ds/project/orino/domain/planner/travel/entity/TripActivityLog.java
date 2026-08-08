package ds.project.orino.domain.planner.travel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 일정 1건에 대한 사후 기록 - 평점·메모(§S-07 기록 영역).
 *
 * <p><b>사진과 분리된 테이블이다.</b> 사진 업로드가 실패해도 평점·메모는 독립적으로
 * 저장돼야 한다 - 여행 중 회선에서 사진 몇 장이 실패했다고 적어둔 감상이 같이 날아가면
 * 다시 적을 마음이 들지 않는다.
 *
 * <p><b>평점은 null이 될 수 있다.</b> 잘못 누른 별을 되돌릴 방법이 없으면 별점은 함정이
 * 된다. "안 매김"과 "1점"은 다른 값이다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "trip_activity_log")
public class TripActivityLog {

    /** 별 개수의 상한. 화면(별 5개)과 검증이 같은 값을 봐야 한다. */
    public static final int MAX_RATING = 5;

    public static final int MIN_RATING = 1;

    /** 메모 길이 상한. 컬럼 길이와 같아야 한다 - 넘치면 DB가 잘라내지 않고 터진다. */
    public static final int MAX_MEMO_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 일정당 최대 1건(UNIQUE). 일정이 지워지면 기록도 함께 사라진다(FK CASCADE). */
    @Column(name = "activity_id", nullable = false, unique = true)
    private Long activityId;

    @Column(length = MAX_MEMO_LENGTH)
    private String memo;

    /**
     * 1~5. null이면 아직 매기지 않았거나 해제한 것이다.
     *
     * <p>컬럼은 {@code TINYINT}인데 {@code Integer}의 기본 매핑은 {@code INTEGER}라
     * {@code ddl-auto: validate}가 타입 불일치로 컨텍스트를 통째로 못 띄운다. 필드를
     * {@code Byte}로 바꾸면 호출부마다 캐스팅이 번지므로 매핑 쪽을 맞춘다.
     */
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer rating;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TripActivityLog() {
    }

    public TripActivityLog(Long activityId, Integer rating, String memo) {
        this.activityId = activityId;
        update(rating, memo);
    }

    /**
     * 평점·메모 덮어쓰기. 둘 다 null이 될 수 있다 - 별을 해제하거나 메모를 지우는 것도
     * 정당한 입력이라 "빈 값이면 무시"하면 지울 방법이 사라진다.
     */
    public void update(Integer rating, String memo) {
        this.rating = rating;
        this.memo = blankToNull(memo);
    }

    /**
     * 아무것도 남지 않은 기록인지. 별을 해제하고 메모까지 지우면 빈 행이 남는데,
     * 그 상태로 두면 {@code hasLog}가 계속 true라 목록에 기록 표시가 남는다.
     */
    public boolean isEmpty() {
        return rating == null && memo == null;
    }

    /** 공백만 남은 메모는 없는 것과 같다 - 저장해두면 빈 기록이 있는 것처럼 보인다. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public Long getId() {
        return id;
    }

    public Long getActivityId() {
        return activityId;
    }

    public String getMemo() {
        return memo;
    }

    public Integer getRating() {
        return rating;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
