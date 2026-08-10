package ds.project.orino.planner.travel.stay.dto;

import ds.project.orino.domain.planner.travel.entity.TripStay;

import java.time.LocalDate;

/**
 * 겹침 거절(409)에 실어 보내는 상대. <b>"겹칩니다"만 말하면 사용자가 할 수 있는 일이 없다</b> —
 * 어느 숙소의 어느 기간과 겹치는지 알아야 그 숙소를 먼저 줄일 수 있다.
 */
public record StayOverlapResponse(
        Long stayId,
        String name,
        LocalDate checkInDate,
        LocalDate checkOutDate
) {

    public static StayOverlapResponse from(TripStay stay) {
        return new StayOverlapResponse(stay.getId(), stay.getName(),
                stay.getCheckInDate(), stay.getCheckOutDate());
    }
}
