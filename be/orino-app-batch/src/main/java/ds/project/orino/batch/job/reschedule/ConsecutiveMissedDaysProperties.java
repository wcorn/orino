package ds.project.orino.batch.job.reschedule;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "batch.consecutive-missed-days")
public record ConsecutiveMissedDaysProperties(
        String cron,
        String zone,
        int thresholdDays,
        int achievementRateThreshold) {
}
