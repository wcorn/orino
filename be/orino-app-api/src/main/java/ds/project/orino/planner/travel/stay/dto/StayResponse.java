package ds.project.orino.planner.travel.stay.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import ds.project.orino.domain.planner.travel.entity.TripStay;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 숙소 1건. 목록·상세·등록 응답이 같은 형태를 쓴다.
 *
 * @param nights 묵는 밤 수. {@code [checkIn, checkOut)} 반열린 구간이라 날짜 차이 그대로다 —
 *               화면이 다시 세지 않도록 서버가 준다
 */
public record StayResponse(
        Long stayId,
        String name,
        Long placeId,
        LocalDate checkInDate,
        LocalDate checkOutDate,

        @JsonFormat(pattern = "HH:mm")
        LocalTime checkInTime,

        @JsonFormat(pattern = "HH:mm")
        LocalTime checkOutTime,

        String bookingUrl,
        String memo,
        long nights
) {

    public static StayResponse from(TripStay stay) {
        return new StayResponse(stay.getId(), stay.getName(), stay.getPlaceId(),
                stay.getCheckInDate(), stay.getCheckOutDate(),
                stay.getCheckInTime(), stay.getCheckOutTime(),
                stay.getBookingUrl(), stay.getMemo(),
                java.time.temporal.ChronoUnit.DAYS.between(
                        stay.getCheckInDate(), stay.getCheckOutDate()));
    }
}
