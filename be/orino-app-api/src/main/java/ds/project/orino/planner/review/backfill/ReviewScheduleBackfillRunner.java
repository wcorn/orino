package ds.project.orino.planner.review.backfill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 기동 시 {@link ReviewScheduleBackfillService}를 한 번 돌린다.
 *
 * <p>백필은 멱등이라(옛 규칙이 놓은 자리에 그대로 있는 행만 손댄다) 매 기동마다 실행돼도 안전하다 —
 * 처음 한 번만 실제로 쓰고, 그 뒤로는 조회 한 번으로 끝난다. 여러 레플리카가 동시에 돌아도 같은
 * 입력에서 같은 값을 계산하므로 결과가 갈리지 않는다.
 *
 * <p>테스트 프로파일에서는 돌리지 않는다 — 컨텍스트가 뜰 때마다 테스트가 심어둔 일정을 건드릴 수 있어서다.
 * 백필 자체는 {@code ReviewScheduleBackfillServiceTest}가 직접 호출해 검증한다.
 */
@Component
@Profile("!test")
public class ReviewScheduleBackfillRunner {

    private static final Logger log = LoggerFactory.getLogger(ReviewScheduleBackfillRunner.class);

    private final ReviewScheduleBackfillService backfillService;

    public ReviewScheduleBackfillRunner(ReviewScheduleBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        try {
            backfillService.run();
        } catch (RuntimeException e) {
            // 백필 실패가 기동을 막지 않게 한다. 다음 기동에서 다시 시도한다(멱등).
            log.error("복습 일정 백필 실패 — 다음 기동에서 재시도한다", e);
        }
    }
}
