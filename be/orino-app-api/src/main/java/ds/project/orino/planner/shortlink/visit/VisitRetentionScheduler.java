package ds.project.orino.planner.shortlink.visit;

import ds.project.orino.domain.planner.shortlink.repository.ShortlinkVisitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 원시 방문 90일 정리(명세 §8.3).
 *
 * <p><b>집계 테이블은 건드리지 않는다.</b> 90일이 지나면 유입 경로·기기·국가는 사라지고
 * 총 방문과 일별 추이만 남는다 — 화면 기본 범위가 30일이라 실사용에서 이 경계는 잘 보이지 않는다.
 *
 * <p>주기 작업은 이것 하나다. 야간 집계 배치를 만들지 않기로 한 대가로 방문 경로에서 쓰기가
 * 둘이 됐고, 대신 여기 남는 일이 삭제뿐이다(D-12).
 *
 * <p>이 배치는 <b>주기 루프 인벤토리에 올리지 않는다</b> — 로컬 MySQL DELETE라 S3·유료 API
 * 표면이 없다(아키텍처 §3.1).
 */
@Component
public class VisitRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(VisitRetentionScheduler.class);
    private static final Duration RETENTION = Duration.ofDays(90);

    private final ShortlinkVisitRepository visitRepository;
    private final Clock clock;

    public VisitRetentionScheduler(ShortlinkVisitRepository visitRepository, Clock clock) {
        this.visitRepository = visitRepository;
        this.clock = clock;
    }

    /** 새벽 3시 30분(KST). 사람이 링크를 누르지 않는 시간대에 지운다. */
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void purgeOldVisits() {
        Instant threshold = clock.instant().minus(RETENTION);
        try {
            int deleted = visitRepository.deleteOlderThan(threshold);
            if (deleted > 0) {
                log.info("shortlink raw visits purged: {} rows older than {}", deleted, threshold);
            }
        } catch (RuntimeException e) {
            // 못 지워도 서비스는 멀쩡하다. 다음 날 다시 시도한다.
            log.warn("shortlink visit purge failed: {}", e.getMessage());
        }
    }
}
