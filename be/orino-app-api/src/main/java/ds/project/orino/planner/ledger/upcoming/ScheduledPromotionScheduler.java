package ds.project.orino.planner.ledger.upcoming;

import ds.project.orino.domain.planner.ledger.entity.LedgerTransaction;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 예정일이 온 <b>직접 예약</b>을 확정으로 올린다.
 *
 * <p>정기 회차와 달리 직접 예약은 이미 원장에 행이 있다(확정 명세 §8.1) — 새로 적을 게 없고
 * 상태만 바뀐다. 그래서 중복 걱정도 없다: 이미 {@code CONFIRMED}인 행은 질의에 걸리지 않는다.
 *
 * <p><b>금액을 묻지 않는다.</b> 재산세처럼 예상액으로 넣어 둔 건({@code estimated})은 확정된
 * 뒤에도 그 표시를 달고 있고, 고지서가 오면 사람이 고친다 — 승인 단계를 만들면 확정되지 않은
 * 채 쌓이고, 쌓이면 잔액이 거짓말을 한다.
 *
 * <p>정기 항목 자동 기록과 <b>같은 주기</b>(매시 정각)다. 한 배치가 두 일을 하게 묶지 않은
 * 이유는 실패가 서로에게 번지지 않게 하기 위해서다.
 */
@Component
public class ScheduledPromotionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPromotionScheduler.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final LedgerTransactionRepository transactionRepository;
    private final Clock clock;

    public ScheduledPromotionScheduler(LedgerTransactionRepository transactionRepository,
                                       Clock clock) {
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void promoteDue() {
        promoteDueOn(LocalDate.now(clock.withZone(ZONE)));
    }

    /** 「오늘」을 밖에서 주는 경로. 테스트가 이 문으로 들어온다. */
    @Transactional
    public int promoteDueOn(LocalDate today) {
        List<LedgerTransaction> due = transactionRepository
                .findAllByStatusAndDeletedAtIsNullAndOccurredOnLessThanEqual(
                        LedgerTransactionStatus.SCHEDULED, today);
        due.forEach(tx -> tx.updateStatus(LedgerTransactionStatus.CONFIRMED));
        if (!due.isEmpty()) {
            log.info("직접 예약 확정: {}건", due.size());
        }
        return due.size();
    }
}
