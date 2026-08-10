package ds.project.orino.domain.planner.travel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 여행 기간의 날짜 1건. <b>v2.1에서 목적지는 여행이 아니라 이 날짜가 갖는다</b> —
 * 타임존·통화·검색 좌표·날씨가 전부 {@link #basePlaceId}(기준 도시)에서 파생된다.
 *
 * <p><b>기간 내 모든 날짜에 행이 반드시 존재한다.</b> 비어 있는 날짜를 허용하면 타임존이 없는
 * 날이 생기고, 그 순간 알림·날씨·상태 판정이 전부 NPE 아니면 조용한 오답이 된다. 여행 생성·기간
 * 변경은 한 트랜잭션에서 날짜 집합을 기간과 일치시킨다.
 *
 * <p><b>구간(Leg) 테이블을 만들지 않는다.</b> 연속된 같은 {@code basePlaceId}를 묶어 파생한다.
 * 구간을 저장하면 날짜와 구간이 어긋날 수 있는 상태가 두 개 생긴다(D-21).
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "trip_day", uniqueConstraints = @UniqueConstraint(
        name = "uk_day_trip_date", columnNames = {"trip_id", "day_date"}))
public class TripDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    /** 컬럼명이 {@code date}가 아닌 이유 — MySQL 함수명과 혼동돼 DDL·쿼리가 읽기 어려워진다. */
    @Column(name = "day_date", nullable = false)
    private LocalDate dayDate;

    /** 기준 도시. {@link PlaceKind#CITY}인 장소만 허용한다(애플리케이션 검증). */
    @Column(name = "base_place_id", nullable = false)
    private Long basePlaceId;

    /** "체크아웃 후 교토역 코인로커에 짐 보관" — 날짜에 붙는 메모라 도시가 바뀌어도 살린다. */
    @Column(name = "city_memo", length = 200)
    private String cityMemo;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TripDay() {
    }

    public TripDay(Long tripId, LocalDate dayDate, Long basePlaceId) {
        this.tripId = tripId;
        this.dayDate = dayDate;
        this.basePlaceId = basePlaceId;
    }

    /**
     * 기준 도시를 바꾼다. <b>이미 담긴 일정의 장소는 건드리지 않는다</b> — 오사카 가게를 교토
     * 날짜에 두는 건 사용자의 선택이다. 타임존·통화·날씨·알림 발송시각의 재계산은 호출부가
     * 한 곳에 모아 처리한다.
     */
    public void changeBaseCity(Long basePlaceId) {
        this.basePlaceId = basePlaceId;
    }

    /** 공백만 남으면 비운다 — 빈 문자열을 저장하면 화면에 메모 자리가 남는다. */
    public void updateCityMemo(String cityMemo) {
        this.cityMemo = cityMemo == null || cityMemo.isBlank() ? null : cityMemo.trim();
    }

    public Long getId() {
        return id;
    }

    public Long getTripId() {
        return tripId;
    }

    public LocalDate getDayDate() {
        return dayDate;
    }

    public Long getBasePlaceId() {
        return basePlaceId;
    }

    public String getCityMemo() {
        return cityMemo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
