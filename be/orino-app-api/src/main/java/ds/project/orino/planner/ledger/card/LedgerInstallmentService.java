package ds.project.orino.planner.ledger.card;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.ledger.entity.LedgerInstallment;
import ds.project.orino.domain.planner.ledger.entity.LedgerInstallmentRound;
import ds.project.orino.domain.planner.ledger.entity.LedgerStatement;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransaction;
import ds.project.orino.domain.planner.ledger.repository.LedgerInstallmentRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerInstallmentRoundRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerStatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 할부. 원 거래 1건 + 회차 N행을 만든다.
 *
 * <p><b>부채는 잔여 원금 전액</b>이다 — 아직 청구되지 않은 회차를 포함하고 무이자 여부와
 * 무관하다. 원 거래에 전액이 적혀 있으므로 카드의 미결제 사용액이 이미 그 값을 담는다:
 * 여기서 다시 더하지 않는다. 두 곳에서 세면 빚이 두 배로 보인다.
 */
@Service
public class LedgerInstallmentService {

    /** 카드사가 파는 범위. 이 밖은 입력 실수로 본다. */
    private static final int MIN_MONTHS = 2;
    private static final int MAX_MONTHS = 60;

    private final LedgerInstallmentRepository installmentRepository;
    private final LedgerInstallmentRoundRepository roundRepository;
    private final LedgerStatementRepository statementRepository;

    public LedgerInstallmentService(LedgerInstallmentRepository installmentRepository,
                                    LedgerInstallmentRoundRepository roundRepository,
                                    LedgerStatementRepository statementRepository) {
        this.installmentRepository = installmentRepository;
        this.roundRepository = roundRepository;
        this.statementRepository = statementRepository;
    }

    /**
     * 할부를 연다. 첫 회차는 이 거래가 편입된 청구서의 결제월이다.
     *
     * <p>나눠떨어지지 않는 나머지는 <b>첫 회차</b>가 받는다. 마지막에 몰면 「끝났는 줄 알았는데
     * 더 나왔다」가 되고, 카드사도 대개 첫 회차에 붙인다.
     */
    @Transactional
    public LedgerInstallment open(LedgerTransaction transaction, int months, boolean interestFree) {
        if (months < MIN_MONTHS || months > MAX_MONTHS) {
            throw new CustomException(ErrorCode.LEDGER_INSTALLMENT_MONTHS_OUT_OF_RANGE);
        }
        LedgerInstallment installment = installmentRepository.save(new LedgerInstallment(
                transaction.getMemberId(), transaction.getId(), months,
                interestFree, transaction.getAmount()));
        transaction.updateInstallmentId(installment.getId());

        YearMonth firstBillingMonth = firstBillingMonthOf(transaction);
        long base = transaction.getAmount() / months;
        long remainder = transaction.getAmount() - base * months;

        List<LedgerInstallmentRound> rounds = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            long amount = base + (i == 0 ? remainder : 0);
            rounds.add(new LedgerInstallmentRound(
                    installment.getId(), i + 1,
                    firstBillingMonth.plusMonths(i).toString(), amount));
        }
        roundRepository.saveAll(rounds);
        return installment;
    }

    /**
     * 중도 상환·취소. 남은 회차를 <b>일괄 정리</b>한다.
     *
     * <p>이미 청구서에 붙은 회차는 건드리지 않는다 — 그 달 청구액은 이미 사람이 낸 금액이고,
     * 뒤늦게 바꾸면 지난 청구서가 다시 쓰인다.
     */
    @Transactional
    public void cancel(Long memberId, Long installmentId) {
        LedgerInstallment installment = installmentRepository
                .findByIdAndMemberId(installmentId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_TRANSACTION_NOT_FOUND));

        for (LedgerInstallmentRound round
                : roundRepository.findAllByInstallmentIdOrderByRoundNoAsc(installmentId)) {
            if (round.getStatementId() == null) {
                round.settle();
            }
        }
        installment.cancel();
    }

    /** 그 카드의 잔여 원금 — 아직 내지 않은 회차의 합. */
    @Transactional(readOnly = true)
    public long outstandingPrincipal(Long memberId) {
        List<Long> ids = installmentRepository
                .findAllByMemberIdAndStatus(memberId, LedgerInstallment.Status.ACTIVE)
                .stream().map(LedgerInstallment::getId).toList();
        return ids.isEmpty() ? 0 : roundRepository.sumOutstanding(ids);
    }

    /**
     * 첫 회차가 잡히는 청구월.
     *
     * <p>사이클이 등록된 카드면 그 거래가 편입된 청구서의 <b>결제월</b>이다 — 할부 1회차는
     * 그 청구서와 함께 빠진다. 사이클이 없으면 산 달을 그대로 쓴다.
     */
    private YearMonth firstBillingMonthOf(LedgerTransaction transaction) {
        if (transaction.getStatementId() == null) {
            return YearMonth.from(transaction.getOccurredOn());
        }
        return statementRepository.findById(transaction.getStatementId())
                .map(LedgerStatement::getPaymentDate)
                .map(YearMonth::from)
                .orElseGet(() -> YearMonth.from(transaction.getOccurredOn()));
    }
}
