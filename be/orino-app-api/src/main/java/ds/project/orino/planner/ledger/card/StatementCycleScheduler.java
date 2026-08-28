package ds.project.orino.planner.ledger.card;

import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerInstallment;
import ds.project.orino.domain.planner.ledger.entity.LedgerInstallmentRound;
import ds.project.orino.domain.planner.ledger.entity.LedgerStatement;
import ds.project.orino.domain.planner.ledger.entity.LedgerStatementStatus;
import ds.project.orino.domain.planner.ledger.repository.LedgerAssetRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerInstallmentRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerInstallmentRoundRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerStatementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * 사이클을 넘긴다 — 마감된 청구서를 <b>확정</b>으로 바꾸고 다음 사이클을 연다.
 *
 * <p>확정 자체는 날짜로도 알 수 있다. 그런데도 배치가 필요한 이유는 <b>다음 청구서 행을
 * 미리 세워 두기</b> 위해서다. 이월 잔액과 할부 회차가 얹힐 곳이 있어야 하고, 사용 건이
 * 하나도 없는 달에도 「이번 달 카드값 0원」이 보여야 한다.
 *
 * <p><b>중복 실행에 안전하다.</b> {@code UNIQUE(card_asset_id, cycle_start)}가 청구서를 하나로
 * 묶고, 확정은 멱등이다 — 「replica 1 전제」에 기대지 않는다(D-2와 같은 태도).
 *
 * <p><b>미납 스캔 배치는 두지 않는다.</b> 미납은 「결제일이 지났는데 안 냈다」는 사실이라
 * 물어볼 때마다 판정하면 된다. 플래그로 저장하면 날짜가 바뀔 때마다 누군가 갱신해야 하고,
 * 갱신을 놓치는 순간 화면이 거짓말을 한다 — 잔액을 컬럼으로 두지 않은 것과 같은 이유다(D-8).
 */
@Component
public class StatementCycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(StatementCycleScheduler.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final LedgerStatementRepository statementRepository;
    private final LedgerAssetRepository assetRepository;
    private final LedgerInstallmentRepository installmentRepository;
    private final LedgerInstallmentRoundRepository roundRepository;
    private final LedgerStatementAssigner assigner;
    private final Clock clock;

    public StatementCycleScheduler(LedgerStatementRepository statementRepository,
                                   LedgerAssetRepository assetRepository,
                                   LedgerInstallmentRepository installmentRepository,
                                   LedgerInstallmentRoundRepository roundRepository,
                                   LedgerStatementAssigner assigner,
                                   Clock clock) {
        this.statementRepository = statementRepository;
        this.assetRepository = assetRepository;
        this.installmentRepository = installmentRepository;
        this.roundRepository = roundRepository;
        this.assigner = assigner;
        this.clock = clock;
    }

    /** 새벽 4시 10분(KST). 사람이 카드를 긁지 않는 시간대에 사이클을 넘긴다. */
    @Scheduled(cron = "0 10 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void rollCycles() {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        int confirmed = 0;
        int opened = 0;

        for (LedgerStatement statement : statementRepository
                .findAllByStatusAndCycleEndBefore(LedgerStatementStatus.COLLECTING, today)) {
            statement.confirm();
            confirmed++;

            LedgerAsset card = assetRepository.findById(statement.getCardAssetId()).orElse(null);
            if (card == null || !card.hasBillingCycle()) {
                continue;
            }
            // 다음 사이클을 연다. 이월과 할부 회차가 얹힐 자리다.
            LedgerStatement next = assigner.findOrCreate(card, LedgerBillingCycle.next(card,
                    new LedgerBillingCycle.Cycle(statement.getCycleStart(),
                            statement.getCycleEnd(), statement.getPaymentDate())));
            attachInstallmentRounds(card, next);
            opened++;
        }

        if (confirmed > 0) {
            log.info("카드 사이클 전환: 확정 {}건, 다음 사이클 {}건", confirmed, opened);
        }
    }

    /**
     * 그 달에 잡히는 할부 회차를 청구서에 붙인다.
     *
     * <p>회차는 살 때 이미 만들어져 있다. 여기서는 <b>어느 청구서에 얹히는지</b>만 정한다 —
     * 그래야 그 달 청구액에 회차분이 들어간다.
     */
    private void attachInstallmentRounds(LedgerAsset card, LedgerStatement statement) {
        List<Long> active = installmentRepository
                .findAllByMemberIdAndStatus(card.getMemberId(), LedgerInstallment.Status.ACTIVE)
                .stream().map(LedgerInstallment::getId).toList();
        if (active.isEmpty()) {
            return;
        }
        String billingMonth = YearMonth.from(statement.getPaymentDate()).toString();
        for (LedgerInstallmentRound round : roundRepository.findUnattached(active, billingMonth)) {
            round.attachTo(statement.getId());
        }
    }
}
