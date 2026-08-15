package ds.project.orino.domain.planner.travel.entity;

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

/**
 * 두 장소 사이를 <b>어떻게 이동하는가</b>(#1208). 사용자가 직접 적는다.
 *
 * <p>예전에는 앱이 직선거리로 도보/자동차를 판정하고 Google Routes로 소요 시간을 사 왔다.
 * 그 방식은 두 가지를 못 했다 — <b>비행기·신칸센·페리를 표현하지 못했고</b>, 도시를 넘는
 * 구간은 아예 계산하지 않았다. 정작 미리 정해 두는 이동이 그 구간이다.
 *
 * <p><b>여행이 아니라 장소 쌍에 붙는다.</b> 일정 쌍에 붙이면 드래그로 순서를 바꾸는 순간 값이
 * 거짓이 되거나 사라진다. 장소 쌍이면 순서를 바꿔도 살아 있고, 같은 두 장소를 다시 이으면
 * 다른 날·다른 여행에서도 그대로 뜬다. {@code travel_place}가 이미 멤버별 테이블이라
 * {@code member_id}까지 묶으면 다른 사용자에게 새지 않는다.
 *
 * <p><b>방향을 유지한다.</b> A→B와 B→A는 다른 행이다 — 편도 항공과 일방통행이 실제로 다르고,
 * 돌아오는 길이 같으리라고 앱이 단정할 근거가 없다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "travel_move")
public class TravelMove {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "from_place_id", nullable = false)
    private Long fromPlaceId;

    @Column(name = "to_place_id", nullable = false)
    private Long toPlaceId;

    /** 아이콘과 묶음에만 쓰는 분류. 무엇을 타는지는 {@link #name}이 말한다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TravelMode mode;

    /** 실제 이동수단 이름 — {@code 나리타 익스프레스 3호} · {@code 피치 MM8}. */
    @Column(length = 100)
    private String name;

    /**
     * 소요 시간(분). <b>null을 허용한다</b> — 수단을 먼저 정하고 시간은 나중에 확인하는 것이
     * 실제 순서다. 아직 안 찾아본 것을 0분으로 적으면 화면이 "바로 옆"이라고 읽는다.
     */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    /** 예매·확인 링크. 현지에서 이 행을 눌러 바로 열 수 있어야 한다. */
    @Column(length = 500)
    private String url;

    /** 좌석·플랫폼·예약번호처럼 표에 칸을 따로 낼 만큼은 아닌 것들. */
    @Column(length = 500)
    private String memo;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TravelMove() {
    }

    public TravelMove(Long memberId, Long fromPlaceId, Long toPlaceId, TravelMode mode) {
        this.memberId = memberId;
        this.fromPlaceId = fromPlaceId;
        this.toPlaceId = toPlaceId;
        this.mode = mode;
    }

    public void update(TravelMode mode, String name, Integer durationMinutes,
                       String url, String memo) {
        this.mode = mode;
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.url = url;
        this.memo = memo;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getFromPlaceId() {
        return fromPlaceId;
    }

    public Long getToPlaceId() {
        return toPlaceId;
    }

    public TravelMode getMode() {
        return mode;
    }

    public String getName() {
        return name;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public String getUrl() {
        return url;
    }

    public String getMemo() {
        return memo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
