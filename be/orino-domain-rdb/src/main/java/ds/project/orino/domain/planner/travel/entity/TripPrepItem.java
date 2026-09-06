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

    /**
     * 분류 안의 묶음 이름. {@code null}이면 묶음 없음이다(#1358).
     *
     * <p><b>분류를 늘리는 대신 한 겹을 넣은 자리다.</b> 분류는 행동이 달라서 넷으로 고정이고
     * 여행이 바뀌어도 안 변한다(D-31). 묶음은 반대다 — 이번 여행에 캐리어와 기내백을 나눌지,
     * 출발 전과 현지에서를 나눌지는 매번 다르다. 그래서 enum이 아니라 사용자가 적는 말이다.
     *
     * <p>묶음의 순서는 저장하지 않는다. 그 안의 최소 {@code displayOrder}가 묶음의 자리다 —
     * 순서를 따로 들면 항목을 옮길 때마다 둘을 맞춰야 하고, 어긋난 것이 화면에는 안 보인다.
     */
    @Column(name = "section_label", length = 30)
    private String sectionLabel;

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

    /**
     * 묶음을 옮긴다. 빈 이름은 「묶음 없음」과 같은 뜻이라 {@code null}로 떨어뜨린다 —
     * 공백 하나짜리 묶음이 생기면 목록에 이름 없는 소제목이 뜨고, 그걸 지울 방법이 없다.
     *
     * <p>새 자리는 호출부가 정해 넘긴다. 항목 하나는 그 묶음에 뭐가 몇 개 있는지 모른다.
     */
    public void changeSectionLabel(String sectionLabel, int displayOrder) {
        this.sectionLabel = normalizeSection(sectionLabel);
        this.displayOrder = displayOrder;
    }

    /** 자리를 그대로 둔 채 이름만. 새로 만들 때처럼 순서가 이미 정해진 자리에서 쓴다. */
    public void changeSectionLabel(String sectionLabel) {
        this.sectionLabel = normalizeSection(sectionLabel);
    }

    /**
     * 이미 그 묶음인가. <b>앞뒤 공백과 빈 문자열은 같은 뜻으로 본다</b> — 「캐리어 」로 저장을
     * 눌렀다고 항목이 묶음 맨 뒤로 뛰면, 손대지 않은 순서가 저장할 때마다 흔들린다.
     */
    public boolean isInSection(String sectionLabel) {
        String normalized = normalizeSection(sectionLabel);
        return this.sectionLabel == null ? normalized == null
                : this.sectionLabel.equals(normalized);
    }

    private static String normalizeSection(String sectionLabel) {
        if (sectionLabel == null) {
            return null;
        }
        String trimmed = sectionLabel.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
     *
     * <p><b>묶음 이름은 반대로 따라간다.</b> 수량은 짐에서만 뜻이 있다고 우리가 정의한 값이지만,
     * 묶음은 사용자가 적은 말이다 — 「캐리어」를 할 일로 옮겼다고 조용히 지우면, 되돌리려 해도
     * 무슨 말을 적었는지가 어디에도 남지 않는다.
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

    public String getSectionLabel() {
        return sectionLabel;
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
