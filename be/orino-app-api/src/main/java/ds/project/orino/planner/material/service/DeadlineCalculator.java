package ds.project.orino.planner.material.service;

import ds.project.orino.domain.material.entity.DeadlineMode;
import ds.project.orino.domain.material.entity.MaterialAllocation;
import ds.project.orino.domain.material.entity.PaceStatus;
import ds.project.orino.domain.material.entity.StudyMaterial;
import ds.project.orino.domain.material.entity.StudyUnit;
import ds.project.orino.domain.material.entity.UnitStatus;
import ds.project.orino.domain.preference.entity.UserPreference;
import ds.project.orino.planner.material.dto.DeadlineProjectionResponse;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * 학습 자료의 데드라인 역산을 수행한다.
 * 남은 단위, 가용 학습일, 하루 필요량, 현재 페이스 상태를 계산한다.
 */
@Component
public class DeadlineCalculator {

    /**
     * 학습 자료의 데드라인 역산 정보를 계산한다.
     *
     * @param material   대상 학습 자료 (units 로딩 필요)
     * @param preference 사용자 설정 (restDays, dailyStudyMinutes)
     * @param today      기준 날짜
     * @return deadlineMode=DEADLINE 이고 deadline 이 설정된 경우 역산 결과,
     *         그렇지 않으면 null
     */
    public DeadlineProjectionResponse calculate(StudyMaterial material,
                                                UserPreference preference,
                                                LocalDate today) {
        if (material.getDeadlineMode() != DeadlineMode.DEADLINE
                || material.getDeadline() == null) {
            return null;
        }

        int totalUnits = material.getUnits().size();
        int completedUnits = (int) material.getUnits().stream()
                .filter(u -> u.getStatus() == UnitStatus.COMPLETED)
                .count();
        int remainingUnits = totalUnits - completedUnits;
        int remainingMinutes = material.getUnits().stream()
                .filter(u -> u.getStatus() != UnitStatus.COMPLETED)
                .mapToInt(StudyUnit::getEstimatedMinutes)
                .sum();

        int availableDays = countAvailableDays(
                today, material.getDeadline(), preference);

        int allocatedMinutesPerDay = resolveAllocatedMinutes(
                material.getAllocation(), preference);

        int requiredUnitsPerDay;
        int requiredMinutesPerDay;
        if (availableDays <= 0) {
            requiredUnitsPerDay = remainingUnits;
            requiredMinutesPerDay = remainingMinutes;
        } else {
            requiredUnitsPerDay = ceilDiv(remainingUnits, availableDays);
            requiredMinutesPerDay = ceilDiv(remainingMinutes, availableDays);
        }

        PaceStatus paceStatus = judgePace(
                remainingUnits, availableDays,
                requiredMinutesPerDay, allocatedMinutesPerDay);

        return new DeadlineProjectionResponse(
                material.getDeadline(),
                totalUnits,
                completedUnits,
                remainingUnits,
                remainingMinutes,
                availableDays,
                requiredUnitsPerDay,
                requiredMinutesPerDay,
                allocatedMinutesPerDay,
                paceStatus);
    }

    private int countAvailableDays(LocalDate today, LocalDate deadline,
                                   UserPreference preference) {
        if (deadline.isBefore(today)) {
            return 0;
        }
        Set<DayOfWeek> restDays = parseRestDays(
                preference != null ? preference.getRestDays() : null);
        int count = 0;
        LocalDate cursor = today;
        while (!cursor.isAfter(deadline)) {
            if (!restDays.contains(cursor.getDayOfWeek())) {
                count++;
            }
            cursor = cursor.plusDays(1);
        }
        return count;
    }

    private int resolveAllocatedMinutes(MaterialAllocation allocation,
                                        UserPreference preference) {
        if (allocation != null) {
            return allocation.getDefaultMinutes();
        }
        return preference != null ? preference.getDailyStudyMinutes() : 0;
    }

    private PaceStatus judgePace(int remainingUnits, int availableDays,
                                 int requiredMinutesPerDay,
                                 int allocatedMinutesPerDay) {
        if (remainingUnits <= 0) {
            return PaceStatus.AHEAD;
        }
        if (availableDays <= 0) {
            return PaceStatus.BEHIND;
        }
        if (allocatedMinutesPerDay <= 0) {
            return requiredMinutesPerDay > 0
                    ? PaceStatus.BEHIND : PaceStatus.ON_TRACK;
        }
        if (requiredMinutesPerDay > allocatedMinutesPerDay) {
            return PaceStatus.BEHIND;
        }
        if (requiredMinutesPerDay < allocatedMinutesPerDay) {
            return PaceStatus.AHEAD;
        }
        return PaceStatus.ON_TRACK;
    }

    private Set<DayOfWeek> parseRestDays(String restDays) {
        if (restDays == null || restDays.isBlank()) {
            return EnumSet.noneOf(DayOfWeek.class);
        }
        Set<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        Arrays.stream(restDays.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::parseDayOfWeek)
                .filter(d -> d != null)
                .forEach(result::add);
        return result;
    }

    private DayOfWeek parseDayOfWeek(String token) {
        return switch (token.toUpperCase()) {
            case "MON" -> DayOfWeek.MONDAY;
            case "TUE" -> DayOfWeek.TUESDAY;
            case "WED" -> DayOfWeek.WEDNESDAY;
            case "THU" -> DayOfWeek.THURSDAY;
            case "FRI" -> DayOfWeek.FRIDAY;
            case "SAT" -> DayOfWeek.SATURDAY;
            case "SUN" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    private int ceilDiv(int dividend, int divisor) {
        if (divisor == 0) {
            return 0;
        }
        return (dividend + divisor - 1) / divisor;
    }
}
