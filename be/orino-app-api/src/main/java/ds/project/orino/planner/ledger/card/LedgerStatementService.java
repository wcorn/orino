package ds.project.orino.planner.ledger.card;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerAssetType;
import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.entity.LedgerInstallmentRound;
import ds.project.orino.domain.planner.ledger.entity.LedgerStatement;
import ds.project.orino.domain.planner.ledger.entity.LedgerStatementStatus;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransaction;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionSource;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;
import ds.project.orino.domain.planner.ledger.repository.LedgerAssetRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerCategoryRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerInstallmentRoundRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerStatementRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.planner.ledger.common.LedgerClock;
import ds.project.orino.planner.ledger.transaction.dto.TransactionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 카드 청구서 — <b>이 모듈의 심장</b>(확정 명세 §7).
 *
 * <p>여기서 지켜지는 것 셋.
 * <ol>
 *   <li><b>카드 사용과 카드 대금은 다른 사건이다.</b> 사용은 지출, 대금 납부는 이체다.
 *       납부로 만드는 거래는 반드시 {@code TRANSFER}이고, 그래서 지출 합계에 절대 잡히지 않는다 —
 *       카드 대금이 지출로 새는 유일한 구멍을 여기서 막는다</li>
 *   <li><b>대금은 자동으로 기록하지 않는다</b>(§7.2). 잔고 부족·리볼빙·선결제·연회비 때문에
 *       실제 출금액을 앱이 알 수 없다. 모르는 걸 아는 척 적어두면 원장이 조용히 틀어진다</li>
 *   <li><b>이월은 지출이 아니다</b>(§7.5). 청구액에는 들어가지만 지출 합계에는 안 들어간다.
 *       수수료만 새 지출이다</li>
 * </ol>
 */
@Service
public class LedgerStatementService {

    private static final List<LedgerStatementStatus> UNSETTLED =
            List.of(LedgerStatementStatus.CONFIRMED, LedgerStatementStatus.PARTIAL);

    /** 프리셋 카테고리 이름. v1에서 이 칸을 미리 만들어 둔 이유가 여기서 쓰인다. */
    private static final String INTEREST_FEE_CATEGORY = "이자/수수료";

    private final LedgerStatementRepository statementRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerInstallmentRoundRepository roundRepository;
    private final LedgerAssetRepository assetRepository;
    private final LedgerCategoryRepository categoryRepository;
    private final LedgerStatementAssigner assigner;
    private final LedgerClock clock;

    public LedgerStatementService(LedgerStatementRepository statementRepository,
                                  LedgerTransactionRepository transactionRepository,
                                  LedgerInstallmentRoundRepository roundRepository,
                                  LedgerAssetRepository assetRepository,
                                  LedgerCategoryRepository categoryRepository,
                                  LedgerStatementAssigner assigner,
                                  LedgerClock clock) {
        this.statementRepository = statementRepository;
        this.transactionRepository = transactionRepository;
        this.roundRepository = roundRepository;
        this.assetRepository = assetRepository;
        this.categoryRepository = categoryRepository;
        this.assigner = assigner;
        this.clock = clock;
    }

    /**
     * 청구액 산식(§7.4). <b>저장하지 않고 그때그때 계산한다.</b>
     *
     * <p>합계만이 아니라 항목을 그대로 낸다 — 화면이 그 줄들을 보여줘야 사람이
     * 「왜 이 금액이지」에 스스로 답할 수 있다.
     */
    @Transactional(readOnly = true)
    public LedgerStatementBreakdown breakdownOf(LedgerStatement statement) {
        long usage = 0;
        long refund = 0;
        for (LedgerTransactionRepository.StatementFlowTotal row
                : transactionRepository.sumByStatement(statement.getId())) {
            if (row.getSource() == LedgerTransactionSource.REFUND) {
                // 카드 사용의 환불은 수입 방향으로 적히지만 여기서는 청구액을 깎는다.
                refund += row.getTotal();
            } else if (row.getType() == LedgerFlow.EXPENSE) {
                usage += row.getTotal();
            }
        }

        long installment = 0;
        for (LedgerInstallmentRound round : roundRepository.findAllByStatementId(statement.getId())) {
            installment += round.getAmount();
        }

        return LedgerStatementBreakdown.of(
                usage, installment, statement.getCarriedOverAmount(),
                statement.getInterestFeeAmount(), statement.getAdjustmentAmount(),
                refund, statement.getDiscountAmount(), statement.getPaidAmount());
    }

    @Transactional(readOnly = true)
    public List<LedgerStatement> statementsOf(Long memberId, Long cardAssetId) {
        LedgerAsset card = requireCard(memberId, cardAssetId);
        return statementRepository.findAllByMemberIdAndCardAssetIdOrderByCycleStartDesc(
                memberId, card.getId());
    }

    @Transactional(readOnly = true)
    public LedgerStatement require(Long memberId, Long statementId) {
        return statementRepository.findByIdAndMemberId(statementId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_STATEMENT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<TransactionView> transactionsOf(Long memberId, Long statementId) {
        require(memberId, statementId);
        List<TransactionView> views = new ArrayList<>();
        for (LedgerTransaction tx : transactionRepository
                .findAllByStatementIdAndDeletedAtIsNullOrderByOccurredOnAscIdAsc(statementId)) {
            views.add(TransactionView.of(tx, null, null, null, List.of()));
        }
        return views;
    }

    /** 미납 — 결제일이 지났는데 아직 다 내지 않은 것. 저장된 플래그가 아니라 질의다. */
    @Transactional(readOnly = true)
    public List<LedgerStatement> overdue(Long memberId) {
        return statementRepository.findOverdue(memberId, UNSETTLED, clock.today());
    }

    /** 다가오는 결제. 「앞으로 나갈 돈」이 여기서 나온다. */
    @Transactional(readOnly = true)
    public List<LedgerStatement> upcoming(Long memberId, int days) {
        return statementRepository.findUpcoming(
                memberId, UNSETTLED, clock.today(), clock.today().plusDays(days));
    }

    /**
     * 결제 처리(§7.3).
     *
     * <p><b>세 가지가 한 트랜잭션이다</b>: 이체 INSERT + 청구서 UPDATE + (부분 납부면) 다음
     * 청구서의 이월 UPDATE. 부분 성공하면 「돈은 나갔는데 청구서는 그대로」거나 그 반대가 되고,
     * 둘 다 원장이 깨진 상태다.
     *
     * @param paymentAssetId 실제로 돈이 빠진 계좌. 기본값은 등록된 결제 계좌지만 <b>그날 다른
     *                       통장에서 냈다면 그쪽</b>이 맞다
     */
    @Transactional
    public LedgerStatement pay(Long memberId, Long statementId, LedgerStatementPayRequest request) {
        LedgerStatement statement = require(memberId, statementId);
        if (statement.getStatus() == LedgerStatementStatus.PAID) {
            throw new CustomException(ErrorCode.LEDGER_STATEMENT_ALREADY_PAID);
        }

        LedgerStatementBreakdown breakdown = breakdownOf(statement);
        long amount = request.amount() != null ? request.amount() : breakdown.remaining();
        if (amount <= 0 || amount > breakdown.remaining()) {
            throw new CustomException(ErrorCode.LEDGER_PAYMENT_EXCEEDS_BILLED);
        }

        LedgerAsset card = requireCard(memberId, statement.getCardAssetId());
        Long fromAssetId = request.paymentAssetId() != null
                ? request.paymentAssetId()
                : card.getPaymentAssetId();
        if (fromAssetId == null) {
            throw new CustomException(ErrorCode.LEDGER_ASSET_REQUIRED);
        }
        assetRepository.findByIdAndMemberId(fromAssetId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_ASSET_NOT_FOUND));

        // 대금 납부는 이체다. 지출이 아니다 — 이 한 줄이 §3-2를 집행한다.
        LedgerTransaction payment = new LedgerTransaction(
                memberId, LedgerFlow.TRANSFER, LedgerTransactionStatus.CONFIRMED,
                request.paidOn() != null ? request.paidOn() : statement.getPaymentDate(),
                amount, fromAssetId, LedgerTransactionSource.CARD_PAYMENT);
        payment.updateCounterAssetId(card.getId());
        payment.updateTitle(card.getName() + " 대금");
        transactionRepository.save(payment);

        statement.recordPayment(amount, payment.getOccurredOn(), payment.getId(),
                breakdown.billed());

        if (statement.getStatus() == LedgerStatementStatus.PARTIAL) {
            carryOver(card, statement, breakdown.billed() - statement.getPaidAmount());
        }
        return statement;
    }

    /**
     * 남은 잔액을 다음 청구서로 넘긴다.
     *
     * <p>넘어간 금액은 다음 청구서에 <b>별도 항목</b>으로 얹힌다 — 이번 달 사용액과 섞지 않는다.
     * 섞으면 다음 달에 「이만큼 썼구나」로 읽히고, 그건 같은 돈을 두 번 세는 것이다.
     */
    private void carryOver(LedgerAsset card, LedgerStatement statement, long remaining) {
        if (remaining <= 0) {
            return;
        }
        LedgerBillingCycle.Cycle next = LedgerBillingCycle.next(card,
                new LedgerBillingCycle.Cycle(statement.getCycleStart(), statement.getCycleEnd(),
                        statement.getPaymentDate()));
        LedgerStatement nextStatement = assigner.findOrCreate(card, next);
        nextStatement.receiveCarryOver(remaining);
        statement.markCarriedTo(nextStatement.getId());
    }

    /**
     * 차액 조정 — 실제 청구액이 다를 때 그 차이를 <b>원인 카테고리와 함께</b> 남긴다.
     *
     * <p>숫자만 남기면 다음 달에 그 금액이 무엇이었는지 알 수 없다. 연회비였는지 미반영 건이었는지가
     * 「왜 이 금액이지」의 답이다.
     */
    @Transactional
    public LedgerStatement adjust(Long memberId, Long statementId,
                                  LedgerStatementAdjustRequest request) {
        LedgerStatement statement = require(memberId, statementId);
        if (request.adjustmentAmount() != null) {
            statement.adjust(request.adjustmentAmount(), request.adjustmentCategoryId());
        }
        if (request.interestFeeAmount() != null && request.interestFeeAmount() != 0) {
            statement.addInterestFee(request.interestFeeAmount());
            recordInterestFeeExpense(memberId, statement, request.interestFeeAmount());
        }
        if (request.discountAmount() != null) {
            statement.addDiscount(request.discountAmount());
        }
        return statement;
    }

    /**
     * 수수료를 <b>새 지출</b>로 남긴다(확정 명세 §7.5).
     *
     * <p>이월 잔액과 수수료를 가르는 지점이다. 이월은 이미 쓸 때 잡힌 돈이라 지출이 아니지만,
     * 리볼빙 수수료·연체 이자는 <b>그때 새로 생긴 비용</b>이다 — 적지 않으면 「왜 갚아도 안
     * 줄지」에 답할 수 없다.
     *
     * <p>이 거래는 <b>청구서에 붙이지 않는다.</b> 청구액에는 {@code interestFeeAmount}로 이미
     * 들어 있어서, 사용 건으로도 붙이면 같은 수수료를 두 번 청구하게 된다.
     */
    private void recordInterestFeeExpense(Long memberId, LedgerStatement statement, long amount) {
        LedgerTransaction fee = new LedgerTransaction(
                memberId, LedgerFlow.EXPENSE, LedgerTransactionStatus.CONFIRMED,
                statement.getPaymentDate(), amount, statement.getCardAssetId(),
                LedgerTransactionSource.ADJUSTMENT);
        fee.updateTitle("카드 이자·수수료");
        categoryRepository
                .findAllByMemberIdAndFlowOrderByDisplayOrderAscIdAsc(memberId, LedgerFlow.EXPENSE)
                .stream()
                .filter(category -> INTEREST_FEE_CATEGORY.equals(category.getName()))
                .findFirst()
                // 프리셋에 「이자/수수료」가 있는 이유가 이것이다(#1259). 없으면 미분류로 남고
                // 「정리할 내역」에 걸린다 — 기록을 막지는 않는다.
                .ifPresent(category -> fee.updateCategoryId(category.getId()));
        transactionRepository.save(fee);
    }

    private LedgerAsset requireCard(Long memberId, Long assetId) {
        LedgerAsset asset = assetRepository.findByIdAndMemberId(assetId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_ASSET_NOT_FOUND));
        if (asset.getType() != LedgerAssetType.CREDIT_CARD) {
            throw new CustomException(ErrorCode.LEDGER_NOT_A_CREDIT_CARD);
        }
        return asset;
    }
}
