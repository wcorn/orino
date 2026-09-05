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
import java.time.LocalDate;

/**
 * 출발까지 챙길 것 하나(v2.2 §11). 네 분류({@link PrepCategory}) 안에서만 산다.
 *
 * <p><b>기한은 D−N으로만 저장한다.</b> 절대 날짜를 적어 두면 출발일을 하루 당기는 순간
 * 「10월 10일까지」가 조용히 하루 늦은 기한이 되고, 화면은 멀쩡해 보여서 아무도 모른다.
 * {@code dueDaysBefore}면 기간을 바꿔도 따라 움직이고 <b>되돌릴 것이 없다</b> — 재계산
 * 연쇄가 아예 생기지 않는다(D-29).
 *
 * <p>그 대가로 기한 지남 판정이 조회 시 파생이다({@link #dueDate}·{@link #isOverdue}).
 * 기준 "오늘"은 서버 로컬 날짜가 아니라 <b>첫날 기준 도시의 오늘</b>이라 호출부가 넘긴다 —
 * 여기만 기기 시간대를 쓰면 출발 전날 밤에 화면과 알림이 하루 어긋난다.
 *
 * <p><b>{@code done}을 {@code checkedAt}으로 두지 않는다.</b> 언제 체크했는지 읽는 화면이
 * 없다. 안 읽는 값을 저장하면 다음 사람이 그걸로 정렬해도 되는 줄 안다 — 체크는 정렬에
 * 관여하지 않는다(§13).
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "trip_prep_item")
public class TripPrepItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrepCategory category;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private boolean done;

    /** {@link PrepCategory#BAG}에서만 쓴다. 다른 분류면 아래 두 메서드가 NULL로 떨어뜨린다. */
    @Column
    private Integer quantity;

    /** 출발 D−N. 절대 날짜가 아니다. */
    @Column(name = "due_days_before")
    private Integer dueDaysBefore;

    @Column(length = 500)
    private String url;

    @Column(length = 500)
    private String memo;

    /** 분류 안에서의 순서. 새 항목은 그 분류의 맨 뒤. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TripPrepItem() {
    }

    public TripPrepItem(Long tripId, Long memberId, PrepCategory category, String title,
                        int displayOrder) {
        this.tripId = tripId;
        this.memberId = memberId;
        this.category = category;
        this.title = title;
        this.displayOrder = displayOrder;
    }

    /**
     * 수량을 바꾼다. {@link PrepCategory#BAG}이 아니면 <b>400이 아니라 NULL</b>로 떨어진다 —
     * 분류를 바꾸다 남은 값 때문에 항목 저장이 막히는 편이 더 나쁘다(API §10).
     *
     * <p>분류를 함께 바꾸는 요청이면 {@link #changeCategory}를 먼저 부른다. 순서가 뒤집히면
     * 옛 분류로 판정해 방금 짐으로 옮긴 항목의 수량이 사라진다.
     */
    public void changeQuantity(Integer quantity) {
        this.quantity = category == PrepCategory.BAG ? quantity : null;
    }

    /** 기한(출발 D−N). {@code null}이면 기한 없음이다. 음수 검증은 호출부가 한다. */
    public void changeDueDaysBefore(Integer dueDaysBefore) {
        this.dueDaysBefore = dueDaysBefore;
    }

    public void changeUrl(String url) {
        this.url = url;
    }

    public void changeMemo(String memo) {
        this.memo = memo;
    }

    public void rename(String title) {
        this.title = title;
    }

    public void check(boolean done) {
        this.done = done;
    }

    /**
     * 분류를 옮긴다. 새 순서는 호출부가 정해 넘긴다 — 그 분류에 뭐가 몇 개 있는지는 항목
     * 하나가 알 수 없다.
     *
     * <p>{@link PrepCategory#BAG}에서 나가면 수량은 함께 사라진다. 「멀티어댑터 2개」를 할
     * 일로 옮겨 놓고 수량만 남으면, 아무도 안 읽는 2가 DB에 남는다.
     */
    public void changeCategory(PrepCategory category, int displayOrder) {
        this.category = category;
        this.displayOrder = displayOrder;
        if (category != PrepCategory.BAG) {
            this.quantity = null;
        }
    }

    public void changeOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    /**
     * 기한 날짜. 저장하지 않고 출발일에서 매번 뺀다 — 그래서 출발일이 움직이면 기한도
     * 따라 움직인다. 기한을 안 정한 항목은 {@code null}이다.
     */
    public LocalDate dueDate(LocalDate startDate) {
        return dueDaysBefore == null ? null : startDate.minusDays(dueDaysBefore);
    }

    /**
     * 기한이 지났는가. <b>체크한 항목은 지나지 않는다</b> — 이미 한 일에 빨간 배지를 다는
     * 것은 사용자가 할 수 있는 일이 없는 경고다.
     *
     * @param today 첫날 기준 도시의 오늘. 서버 로컬 날짜를 넘기지 않는다
     */
    public boolean isOverdue(LocalDate startDate, LocalDate today) {
        LocalDate due = dueDate(startDate);
        return !done && due != null && due.isBefore(today);
    }

    public Long getId() {
        return id;
    }

    public Long getTripId() {
        return tripId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public PrepCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public boolean isDone() {
        return done;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Integer getDueDaysBefore() {
        return dueDaysBefore;
    }

    public String getUrl() {
        return url;
    }

    public String getMemo() {
        return memo;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
