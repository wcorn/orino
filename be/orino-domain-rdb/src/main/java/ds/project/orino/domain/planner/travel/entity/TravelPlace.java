package ds.project.orino.domain.planner.travel.entity;

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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * 장소 캐시 하나. 구글에서 가져온 장소 또는 직접 입력한 장소를 담는다.
 *
 * <p><b>여행에 종속되지 않는다.</b> 이전 여행에서 평점 4 이상을 준 장소에 {@code ⭐ 좋았던 곳}
 * 배지를 다는 요구가 있어, 장소는 여행을 가로질러 멤버 단위로 재사용돼야 한다.
 *
 * <p>1단계에서는 아무것도 쓰지 않는다. {@link Trip#getDestinationPlaceId()}·
 * {@link TripActivity#getPlaceId()} FK를 나중에 붙이면 ALTER가 필요해 테이블만 미리 두고,
 * 2단계(장소 검색)부터 채우기 시작한다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "travel_place")
public class TravelPlace {

    /** 영업시간·사진 캐시 유효기간. 이 기간이 지나면 구글에서 다시 받아온다. */
    public static final Duration DETAILS_TTL = Duration.ofDays(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 구글 장소 식별자. 직접 입력한 장소면 null(멤버당 여러 건 허용). */
    @Column(name = "google_place_id", length = 255)
    private String googlePlaceId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String address;

    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(length = 100)
    private String category;

    @Column(length = 50)
    private String phone;

    /** 구글 평점(0.0~5.0). */
    @Column(precision = 2, scale = 1)
    private BigDecimal rating;

    /** 영업시간 원본 JSON. 구조를 해석하지 않고 그대로 캐시했다가 FE에 넘긴다. */
    @Column(name = "opening_hours", columnDefinition = "JSON")
    private String openingHours;

    /** MinIO에 캐시한 대표 사진 key. 공개 URL은 base + key로 조립한다. */
    @Column(name = "photo_object_key", length = 512)
    private String photoObjectKey;

    /** Google 사진 저작자 표기. 사진을 쓰면 함께 노출해야 한다. */
    @Column(name = "photo_attribution", length = 500)
    private String photoAttribution;

    /** 영업시간·사진을 마지막으로 갱신한 시각. null이면 아직 상세를 받은 적 없다. */
    @Column(name = "details_refreshed_at")
    private Instant detailsRefreshedAt;

    /**
     * 구글 검색이 아니라 사용자가 직접 입력한 장소인지.
     * 컬럼명이 {@code manual}이 아닌 이유 — MySQL 8.4의 예약어라 그대로 쓰면 DDL이 깨진다.
     */
    @Column(name = "manual_entry", nullable = false)
    private boolean manualEntry = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TravelPlace() {
    }

    private TravelPlace(Long memberId, String googlePlaceId, String name, boolean manualEntry) {
        this.memberId = memberId;
        this.googlePlaceId = googlePlaceId;
        this.name = name;
        this.manualEntry = manualEntry;
    }

    /** 구글 검색 결과로 담은 장소. */
    public static TravelPlace fromGoogle(Long memberId, String googlePlaceId, String name) {
        return new TravelPlace(memberId, googlePlaceId, name, false);
    }

    /** 검색에 안 나오는 곳을 직접 입력한 장소. {@code google_place_id}가 없다. */
    public static TravelPlace manual(Long memberId, String name) {
        return new TravelPlace(memberId, null, name, true);
    }

    /** 위치·주소·분류 등 검색 응답으로 채워지는 기본 정보. */
    public void updateBasics(String address, BigDecimal lat, BigDecimal lng,
                             String category, BigDecimal rating) {
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.category = category;
        this.rating = rating;
    }

    /** 상세 조회(영업시간·전화·사진) 결과를 채우고 갱신 시각을 찍는다. */
    public void updateDetails(String phone, String openingHours, String photoObjectKey,
                              String photoAttribution, Instant refreshedAt) {
        this.phone = phone;
        this.openingHours = openingHours;
        this.photoObjectKey = photoObjectKey;
        this.photoAttribution = photoAttribution;
        this.detailsRefreshedAt = refreshedAt;
    }

    /** 캐시된 상세가 없거나 {@link #DETAILS_TTL}이 지났으면 재조회 대상이다. */
    public boolean needsDetailsRefresh(Instant now) {
        return detailsRefreshedAt == null || detailsRefreshedAt.plus(DETAILS_TTL).isBefore(now);
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getGooglePlaceId() {
        return googlePlaceId;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public BigDecimal getLng() {
        return lng;
    }

    public String getCategory() {
        return category;
    }

    public String getPhone() {
        return phone;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public String getOpeningHours() {
        return openingHours;
    }

    public String getPhotoObjectKey() {
        return photoObjectKey;
    }

    public String getPhotoAttribution() {
        return photoAttribution;
    }

    public Instant getDetailsRefreshedAt() {
        return detailsRefreshedAt;
    }

    public boolean isManualEntry() {
        return manualEntry;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
