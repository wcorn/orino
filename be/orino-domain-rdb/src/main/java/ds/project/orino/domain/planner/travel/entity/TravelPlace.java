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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * 장소 캐시 하나. 구글에서 가져온 장소 또는 직접 입력한 장소를 담는다.
 *
 * <p><b>여행에 종속되지 않는다.</b> 같은 장소를 일정마다 새 행으로 만들면 영업시간·좌표 캐시가
 * 장소 수만큼 중복되고, 한 행만 갱신돼 나머지가 옛 값으로 남는다. {@code uk_place_member_google}이
 * 멤버당 한 행을 강제한다.
 *
 * <p><b>v2.1 — 도시도 장소다.</b> {@link PlaceKind#CITY}로 표시된 행은 날짜의 기준 도시
 * ({@link TripDay#getBasePlaceId()})가 되어 타임존·통화·날씨 좌표의 주인이 된다. v2.0에서
 * {@code trip}이 들고 있던 값들이 이 자리로 내려왔다.
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

    /** 이 장소가 속한 도시 표시명. 도시 장소 자신이면 자기 이름이 들어간다. */
    @Column(name = "city_name", length = 100)
    private String cityName;

    /**
     * 도시 식별자(구글 장소 id). <b>도시 일치 판정은 이 값으로만 한다</b> — 좌표 거리 임계로
     * 하면 오사카-교토(43km)와 도쿄-요코하마(30km)에서 서로 다른 답이 나온다(D-23).
     */
    @Column(name = "city_place_ref", length = 255)
    private String cityPlaceRef;

    /** ISO 3166-1 alpha-2. 통화·번역 목적 언어를 파생한다. */
    @Column(name = "country_code", length = 2)
    private String countryCode;

    /** IANA 타임존 ID. 기준 도시로 쓰이는 장소에는 반드시 있어야 한다. */
    @Column(length = 64)
    private String timezone;

    /** ISO 4217 통화 코드. */
    @Column(length = 3)
    private String currency;

    /** 도시인지 일반 장소인지. {@link PlaceKind#CITY}만 기준 도시로 지정할 수 있다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "place_kind", nullable = false, length = 20)
    private PlaceKind placeKind = PlaceKind.POI;

    /** 영업시간을 마지막으로 갱신한 시각. null이면 아직 상세를 받은 적 없다. */
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

    /**
     * 검색을 거치지 않고 이름만으로 만든 <b>도시</b> 장소. 목적지를 직접 입력한 여행이
     * 기준 도시를 가지려면 도시 행이 있어야 한다.
     */
    public static TravelPlace manualCity(Long memberId, String name,
                                         String timezone, String currency) {
        TravelPlace place = new TravelPlace(memberId, null, name, true);
        place.promoteToCity(name, timezone, currency);
        return place;
    }

    /**
     * 이 장소를 기준 도시로 쓸 수 있게 만든다. 타임존·통화의 주인이 여행에서 도시로 넘어왔으므로,
     * 도시로 승격하는 순간 두 값을 함께 받는다.
     *
     * <p>{@code cityPlaceRef}는 도시 자신의 식별자다 — 도시 장소에서는 자기 구글 id가 곧
     * 도시 식별자라, 일정 장소({@code POI})의 {@code cityPlaceRef}와 같은 축에서 비교된다.
     */
    public void promoteToCity(String cityName, String timezone, String currency) {
        this.placeKind = PlaceKind.CITY;
        this.cityName = cityName;
        this.timezone = timezone;
        this.currency = currency;
        if (this.cityPlaceRef == null) {
            this.cityPlaceRef = this.googlePlaceId;
        }
    }

    /** 이 장소가 어느 도시에 속하는지. 일정 장소의 도시 이탈 판정(§1124)이 이 값을 본다. */
    public void updateCityInfo(String cityName, String cityPlaceRef, String countryCode) {
        this.cityName = cityName;
        this.cityPlaceRef = cityPlaceRef;
        this.countryCode = countryCode;
    }

    public boolean isCity() {
        return placeKind == PlaceKind.CITY;
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

    /**
     * 좌표만 채운다. 도시 장소는 검색을 거치지 않고 만들어질 수 있어 좌표가 비는데,
     * 좌표가 없으면 그 도시 날짜의 날씨가 통째로 사라진다.
     */
    public void updateCoordinates(BigDecimal lat, BigDecimal lng) {
        this.lat = lat;
        this.lng = lng;
    }

    /** 상세 조회(영업시간·전화·사진) 결과를 채우고 갱신 시각을 찍는다. */
    public void updateDetails(String phone, String openingHours, Instant refreshedAt) {
        this.phone = phone;
        this.openingHours = openingHours;
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

    public String getCityName() {
        return cityName;
    }

    public String getCityPlaceRef() {
        return cityPlaceRef;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getCurrency() {
        return currency;
    }

    public PlaceKind getPlaceKind() {
        return placeKind;
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
