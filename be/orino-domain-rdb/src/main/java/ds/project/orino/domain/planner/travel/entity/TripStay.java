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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 숙소 1건. <b>기준 도시와 분리된 별도 엔티티다</b> — "오늘 있는 도시"와 "오늘 자는 곳"이
 * 다를 수 있다. 닛코 당일치기 날의 기준 도시는 닛코지만 자는 곳은 도쿄다.
 *
 * <p>날짜 판정은 반열린 구간 {@code [checkIn, checkOut)}으로 한다 —
 * 체크아웃일 밤은 이미 다른 곳에서 잔다.
 *
 * <pre>
 * stayTonight(day)  = checkInDate &lt;= day &lt;  checkOutDate   오늘 밤 자는 곳
 * stayCheckout(day) = checkOutDate == day                    오늘 체크아웃하는 곳
 * </pre>
 *
 * <p>겹치는 기간의 숙소는 저장하지 않는다. 겹침을 허용하면 "오늘 밤 어디서 자는가"에 답이
 * 둘이 되고, 화면은 그중 하나를 임의로 고를 수밖에 없다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "trip_stay")
public class TripStay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(nullable = false, length = 100)
    private String name;

    /** 좌표·도시 판정에 쓴다. 직접 입력한 숙소면 null. */
    @Column(name = "place_id")
    private Long placeId;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    /** 항상 {@code > checkInDate} — 애플리케이션에서 검증한다. */
    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    /** 벽시계 시각. UTC로 변환하지 않는다 — 15:00 체크인은 어느 도시에서든 15:00이다. */
    @Column(name = "check_in_time")
    private LocalTime checkInTime;

    @Column(name = "check_out_time")
    private LocalTime checkOutTime;

    @Column(name = "booking_url", length = 500)
    private String bookingUrl;

    @Column(length = 500)
    private String memo;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TripStay() {
    }

    public TripStay(Long tripId, String name, LocalDate checkInDate, LocalDate checkOutDate) {
        this.tripId = tripId;
        this.name = name;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
    }

    public void updateBasics(String name, Long placeId, LocalDate checkInDate,
                             LocalDate checkOutDate) {
        this.name = name;
        this.placeId = placeId;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
    }

    public void updateDetails(LocalTime checkInTime, LocalTime checkOutTime,
                              String bookingUrl, String memo) {
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
        this.bookingUrl = bookingUrl;
        this.memo = memo;
    }

    /**
     * 여행 기간이 줄어 체크아웃일이 기간 밖으로 밀렸을 때 종료일까지 당긴다.
     * 그 결과 하루도 남지 않으면({@code in >= out}) 숙소 자체를 지워야 한다 —
     * 판단은 호출부가 {@link #isEmptyPeriod()}로 한다.
     */
    public void shrinkCheckOutTo(LocalDate newCheckOutDate) {
        this.checkOutDate = newCheckOutDate;
    }

    /** 체크인이 체크아웃과 같거나 뒤면 묵는 밤이 없다. */
    public boolean isEmptyPeriod() {
        return !checkInDate.isBefore(checkOutDate);
    }

    /** 그날 밤 여기서 자는가. 체크아웃일은 포함하지 않는다. */
    public boolean coversNight(LocalDate date) {
        return !date.isBefore(checkInDate) && date.isBefore(checkOutDate);
    }

    /** 그날 여기서 체크아웃하는가. */
    public boolean isCheckOutOn(LocalDate date) {
        return checkOutDate.equals(date);
    }

    /**
     * 다른 숙소와 묵는 밤이 겹치는가. {@code [in, out)} 반열린 구간이라
     * {@code 10.24–10.27} 다음의 {@code 10.27–10.29}는 겹침이 아니다(이동일).
     */
    public boolean overlaps(LocalDate otherCheckIn, LocalDate otherCheckOut) {
        return checkInDate.isBefore(otherCheckOut) && otherCheckIn.isBefore(checkOutDate);
    }

    public Long getId() {
        return id;
    }

    public Long getTripId() {
        return tripId;
    }

    public String getName() {
        return name;
    }

    public Long getPlaceId() {
        return placeId;
    }

    public LocalDate getCheckInDate() {
        return checkInDate;
    }

    public LocalDate getCheckOutDate() {
        return checkOutDate;
    }

    public LocalTime getCheckInTime() {
        return checkInTime;
    }

    public LocalTime getCheckOutTime() {
        return checkOutTime;
    }

    public String getBookingUrl() {
        return bookingUrl;
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
