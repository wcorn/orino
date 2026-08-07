package ds.project.orino.planner.travel.trip.dto;

import ds.project.orino.domain.planner.travel.entity.TripStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 여행 상세. 수정 화면(S-03)이 폼을 그대로 채울 수 있도록 저장된 값을 전부 내려주고,
 * 파생값({@code status}·{@code dDay}·{@code totalDays})을 덧붙인다.
 */
public record TripDetail(
        Long id,
        String title,
        String destinationName,
        Long destinationPlaceId,
        LocalDate startDate,
        LocalDate endDate,
        String timezone,
        String currency,
        BigDecimal lat,
        BigDecimal lng,
        int defaultNotifyMinutes,
        boolean morningSummaryEnabled,
        TripStatus status,
        long dDay,
        int totalDays,
        long activityCount
) {
}
