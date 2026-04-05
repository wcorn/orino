package ds.project.orino.batch.job.reschedule;

import ds.project.orino.domain.calendar.entity.DailySchedule;
import ds.project.orino.domain.calendar.repository.DailyScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 연속으로 성취율이 낮은 사용자를 감지해 미래 DailySchedule에 dirty 플래그를 세운다.
 * 다음 스케줄 조회 시 SchedulingEngine이 lazy 재생성하여 리스케줄링을 트리거한다.
 */
@Component
public class ConsecutiveMissedDaysDetector {

    private static final Logger log =
            LoggerFactory.getLogger(ConsecutiveMissedDaysDetector.class);

    private final DailyScheduleRepository dailyScheduleRepository;

    public ConsecutiveMissedDaysDetector(
            DailyScheduleRepository dailyScheduleRepository) {
        this.dailyScheduleRepository = dailyScheduleRepository;
    }

    @Transactional
    public int detectAndMarkDirty(LocalDate today, int thresholdDays,
                                  int achievementRateThreshold) {
        LocalDate windowStart = today.minusDays(thresholdDays);
        LocalDate windowEnd = today.minusDays(1);

        List<DailySchedule> recent = dailyScheduleRepository
                .findByScheduleDateBetween(windowStart, windowEnd);

        Map<Long, List<DailySchedule>> byMember = recent.stream()
                .filter(s -> s.getTotalBlocks() > 0)
                .collect(Collectors.groupingBy(s -> s.getMember().getId()));

        int affectedMembers = 0;
        for (Map.Entry<Long, List<DailySchedule>> entry : byMember.entrySet()) {
            List<DailySchedule> schedules = entry.getValue();
            if (schedules.size() < thresholdDays) {
                continue;
            }
            boolean allMissed = schedules.stream()
                    .allMatch(s -> achievementRate(s) < achievementRateThreshold);
            if (!allMissed) {
                continue;
            }
            int updated = dailyScheduleRepository
                    .markDirtyFromDate(entry.getKey(), today);
            if (updated > 0) {
                affectedMembers++;
                log.info("memberId={} consecutive {} days missed, "
                                + "marked {} future schedules dirty",
                        entry.getKey(), thresholdDays, updated);
            }
        }
        return affectedMembers;
    }

    private static int achievementRate(DailySchedule schedule) {
        if (schedule.getTotalBlocks() == 0) {
            return 0;
        }
        return schedule.getCompletedBlocks() * 100 / schedule.getTotalBlocks();
    }
}
