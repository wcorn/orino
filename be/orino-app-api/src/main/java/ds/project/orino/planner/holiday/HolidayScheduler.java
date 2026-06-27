package ds.project.orino.planner.holiday;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 공휴일 동기화 트리거: 기동 직후 1회 + 매일 00:00(KST).
 * 멱등 upsert라 단일/다중 인스턴스 모두 안전하며, 실패해도 앱에 영향을 주지 않는다(로그만).
 */
@Component
public class HolidayScheduler {

    private static final Logger log = LoggerFactory.getLogger(HolidayScheduler.class);

    private final HolidaySyncService syncService;

    public HolidayScheduler(HolidaySyncService syncService) {
        this.syncService = syncService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        runSafely();
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void daily() {
        runSafely();
    }

    private void runSafely() {
        try {
            syncService.syncUpcomingYears();
        } catch (RuntimeException e) {
            log.warn("holiday sync failed: {}", e.getMessage());
        }
    }
}
