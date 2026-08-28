package ds.project.orino.planner.ledger.recurring;

import ds.project.orino.domain.planner.ledger.entity.LedgerAmountType;
import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.entity.LedgerRecurring;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransaction;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionSource;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;
import ds.project.orino.domain.planner.ledger.repository.LedgerAssetRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.planner.ledger.card.LedgerStatementAssigner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 회차 하나를 원장에 적는다.
 *
 * <p><b>회차마다 트랜잭션이 따로다</b>({@code REQUIRES_NEW}). 한 회차가 중복으로 튕겨도
 * 나머지 회차는 그대로 적혀야 하기 때문이다 — 한 트랜잭션에 묶으면 밀린 6개월치를 따라잡다가
 * 다섯 번째에서 걸리면 앞의 넷까지 사라진다.
 *
 * <p><b>중복은 여기서 막지 않는다.</b> {@code UNIQUE(recurring_id, occurrence_date)}가
 * 막고, 예외는 호출자가 잡아 넘긴다(D-2). 애플리케이션이 「이미 있나?」를 먼저 조회하는
 * 방식은 두 인스턴스가 같은 순간 조회하면 둘 다 없다고 답하므로 아무것도 막지 못한다.
 */
@Component
public class LedgerRecurringPoster {

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerAssetRepository assetRepository;
    private final LedgerStatementAssigner statementAssigner;

    public LedgerRecurringPoster(LedgerTransactionRepository transactionRepository,
                                 LedgerAssetRepository assetRepository,
                                 LedgerStatementAssigner statementAssigner) {
        this.transactionRepository = transactionRepository;
        this.assetRepository = assetRepository;
        this.statementAssigner = statementAssigner;
    }

    /**
     * 그 회차를 적는다. 이미 적혀 있으면 {@code DataIntegrityViolationException}이 올라간다 —
     * 호출자에게 그건 <b>정상 경로</b>다.
     *
     * @param occurrenceDate 규칙이 계산한 원래 예정일. 중복 방지의 키다
     * @param postedOn       실제로 적히는 날짜. 영업일 보정·이동이 반영된 값이다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LedgerTransaction post(LedgerRecurring rule, LocalDate occurrenceDate,
                                  LocalDate postedOn, long amount, LocalDate today) {
        LedgerAsset asset = assetRepository.findById(rule.getAssetId()).orElse(null);
        if (asset == null) {
            return null;
        }
        LedgerTransactionStatus status = postedOn.isAfter(today)
                ? LedgerTransactionStatus.SCHEDULED
                : LedgerTransactionStatus.CONFIRMED;

        LedgerTransaction tx = new LedgerTransaction(rule.getMemberId(), rule.getTxType(), status,
                postedOn, amount, asset.getId(), LedgerTransactionSource.RECURRING);
        tx.updateCounterAssetId(rule.getCounterAssetId());
        tx.updateCategoryId(rule.getCategoryId());
        tx.updateTitle(rule.getName());
        // 변동 금액(공과금)은 예상액으로 적힌다. 고지서가 오면 고쳐야 한다는 뜻이고,
        // 「자동」과 달리 이건 실제로 대기 상태다 — 화면이 그 사실을 알린다.
        tx.updateEstimated(rule.getAmountType() == LedgerAmountType.VARIABLE);
        tx.updateRecurrence(rule.getId(), occurrenceDate);

        // 여기서 튕기면 이미 적힌 회차라는 뜻이다. 조회로 먼저 확인하지 않는다.
        transactionRepository.saveAndFlush(tx);

        // 카드로 나가는 정기 항목은 적히는 순간 그 카드 청구서에 편입된다(#1262와 같은 경로).
        if (rule.getTxType() == LedgerFlow.EXPENSE) {
            statementAssigner.resolveFor(asset, postedOn)
                    .ifPresent(statement -> tx.updateStatementId(statement.getId()));
        }
        return tx;
    }
}
