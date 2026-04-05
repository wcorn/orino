package ds.project.orino.batch.job.reschedule;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ConsecutiveMissedDaysJob {

    private static final Logger log =
            LoggerFactory.getLogger(ConsecutiveMissedDaysJob.class);

    private final ConsecutiveMissedDaysDetector detector;
    private final ConsecutiveMissedDaysProperties properties;

    public ConsecutiveMissedDaysJob(
            ConsecutiveMissedDaysDetector detector,
            ConsecutiveMissedDaysProperties properties) {
        this.detector = detector;
        this.properties = properties;
    }

    @Scheduled(
            cron = "${batch.consecutive-missed-days.cron}",
            zone = "${batch.consecutive-missed-days.zone}")
    @SchedulerLock(
            name = "consecutiveMissedDaysJob",
            lockAtLeastFor = "PT1M",
            lockAtMostFor = "PT10M")
    public void run() {
        LocalDate today = LocalDate.now();
        log.info("ConsecutiveMissedDaysJob start today={} thresholdDays={}",
                today, properties.thresholdDays());
        int affected = detector.detectAndMarkDirty(
                today,
                properties.thresholdDays(),
                properties.achievementRateThreshold());
        log.info("ConsecutiveMissedDaysJob done affectedMembers={}", affected);
    }
}
