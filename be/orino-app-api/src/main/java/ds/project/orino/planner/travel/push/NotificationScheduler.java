package ds.project.orino.planner.travel.push;

import ds.project.orino.planner.travel.push.service.NotificationDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 발송 폴링(§6).
 *
 * <p>30초마다 예약 시각이 지난 알림을 처리한다. 정각 알림이 최대 30초 늦을 수 있는데,
 * "15분 전 알림"에서 30초는 문제가 되지 않는다 — 대신 폴링 간격을 짧게 해 부하를 올릴 이유도
 * 없다.
 *
 * <p><b>replica 1 전제다.</b> 여러 인스턴스가 돌면 같은 알림을 중복 발송한다. 그때는
 * DB 조건부 UPDATE로 락을 넣어야 한다 —
 * <a href="https://github.com/wcorn/orino/wiki/Travel-Open-Items">결정 기록</a> 참조.
 *
 * <p>{@code HolidayScheduler}와 달리 기동 시 1회 실행을 넣지 않는다. 기동 직후엔 밀린 알림이
 * 쏟아질 수 있고, 30초 뒤 첫 폴링이 어차피 같은 일을 한다.
 */
@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final NotificationDispatchService dispatchService;

    public NotificationScheduler(NotificationDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelayString = "${travel.push.poll-interval:30s}")
    public void dispatch() {
        try {
            int sent = dispatchService.dispatchDue();
            if (sent > 0) {
                log.info("웹푸시 발송 {}건", sent);
            }
        } catch (RuntimeException e) {
            // 폴링이 예외로 죽으면 다음 주기부터 알림이 통째로 멈춘다.
            log.warn("웹푸시 발송 폴링 실패: {}", e.getMessage());
        }
    }
}
