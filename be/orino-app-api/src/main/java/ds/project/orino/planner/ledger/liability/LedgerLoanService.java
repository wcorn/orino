package ds.project.orino.planner.ledger.liability;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerBusinessDayPolicy;
import ds.project.orino.domain.planner.ledger.entity.LedgerLoan;
import ds.project.orino.domain.planner.ledger.entity.LedgerRateHistory;
import ds.project.orino.domain.planner.ledger.entity.LedgerSettings;
import ds.project.orino.domain.planner.ledger.repository.LedgerAssetRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerLoanRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerLoanRepository.LoanPrincipal;
import ds.project.orino.domain.planner.ledger.repository.LedgerRateHistoryRepository;
import ds.project.orino.planner.ledger.common.LedgerClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 대출(`LDG-008`). <b>잔여 원금의 정본은 은행이다</b> — 여기서는 기준 원금에 기준일 이후 원장
 * 이체를 더해 추정하고, 어긋나면 기준값을 다시 맞춘다(덧붙인 명세 §4.2).
 *
 * <p>상환 회차 전개와 상환 처리는 여기 없다(#1401). 이 서비스는 대출이라는 <b>자산</b>과
 * 잔여 원금이라는 <b>부채</b>까지만 연다.
 */
@Service
public class LedgerLoanService {

    private static final int MAX_PAYMENT_DAY = 28;
    /** {@code DECIMAL(6,3)}이 담을 수 있는 가장 큰 값. */
    private static final BigDecimal MAX_RATE = new BigDecimal("999.999");
    private static final int RATE_SCALE = 3;

    private final LedgerAssetRepository assetRepository;
    private final LedgerLoanRepository loanRepository;
    private final LedgerRateHistoryRepository rateRepository;
    private final LedgerClock clock;

    public LedgerLoanService(LedgerAssetRepository assetRepository,
                             LedgerLoanRepository loanRepository,
                             LedgerRateHistoryRepository rateRepository,
                             LedgerClock clock) {
        this.assetRepository = assetRepository;
        this.loanRepository = loanRepository;
        this.rateRepository = rateRepository;
        this.clock = clock;
    }

    /**
     * 대출 자산을 만들 때 대출 속성과 <b>금리 첫 행</b>을 함께 쓴다(아키텍처 §10.4). 자산 행은
     * 호출한 쪽이 먼저 저장한다 — 같은 트랜잭션이라 여기서 거부되면 자산도 남지 않는다.
     */
    @Transactional
    public void open(Long memberId, LedgerAsset asset, LoanDtos.Open request) {
        if (request == null) {
            throw new CustomException(ErrorCode.LEDGER_LOAN_INVALID);
        }
        validateTerms(memberId, request);

        LocalDate today = clock.today();
        long baselinePrincipal = request.baselinePrincipal() != null ? request.baselinePrincipal() : 0L;
        LocalDate baselineAsOf = request.baselineAsOf() != null ? request.baselineAsOf() : today;
        validateBaseline(baselinePrincipal, baselineAsOf, today);

        loanRepository.save(new LedgerLoan(
                asset.getId(), request.repaymentMethod(), request.paymentAssetId(),
                request.paymentDay(),
                request.businessDayPolicy() != null
                        ? request.businessDayPolicy() : LedgerBusinessDayPolicy.AS_IS,
                request.startedOn(), request.maturityDate(), request.originalPrincipal(),
                baselinePrincipal, baselineAsOf));
        // 첫 금리는 실행일부터다. 그 뒤에 바뀐 금리는 금리 변경으로 한 줄씩 더한다.
        rateRepository.save(new LedgerRateHistory(
                asset.getId(), request.startedOn(), normalized(request.annualRate())));
    }

    @Transactional(readOnly = true)
    public LoanDtos.Response get(Long memberId, Long assetId) {
        LedgerAsset asset = requireLoanAsset(memberId, assetId);
        LedgerLoan loan = requireLoan(assetId);
        List<LedgerRateHistory> rates = rateRepository.findAllByAssetIdOrderByEffectiveFromDesc(assetId);
        String paymentAssetName = assetRepository.findByIdAndMemberId(loan.getPaymentAssetId(), memberId)
                .map(LedgerAsset::getName)
                .orElse(null);

        return new LoanDtos.Response(
                asset.getId(), asset.getName(), principalOf(memberId, loan),
                loan.getRepaymentMethod(), loan.getPaymentAssetId(), paymentAssetName,
                loan.getPaymentDay(), loan.getBusinessDayPolicy(),
                loan.getStartedOn(), loan.getMaturityDate(), loan.getOriginalPrincipal(),
                new LoanDtos.Baseline(loan.getBaselinePrincipal(), loan.getBaselineAsOf()),
                currentRate(rates, clock.today()),
                rates.stream().map(LoanDtos.Rate::of).toList());
    }

    /**
     * 은행 앱의 잔여 원금으로 기준값을 다시 맞춘다. <b>거래를 만들지 않는다</b>(D-18) — 조정
     * 거래를 세우면 그 차액이 어느 달의 수입이나 지출로 원장에 남는다.
     *
     * <p>바꾸기 직전 추정을 <b>같은 트랜잭션에서</b> 읽고 쓴다(아키텍처 §10.4). 따로 읽으면 그
     * 사이에 적힌 이체가 차이에 섞인다.
     */
    @Transactional
    public LoanDtos.BaselineChange updateBaseline(Long memberId, Long assetId,
                                                  LoanDtos.BaselineRequest request) {
        requireLoanAsset(memberId, assetId);
        LedgerLoan loan = requireLoan(assetId);
        LocalDate today = clock.today();
        if (request.principal() == null) {
            throw new CustomException(ErrorCode.LEDGER_BASELINE_INVALID);
        }
        LocalDate asOf = request.asOf() != null ? request.asOf() : today;
        validateBaseline(request.principal(), asOf, today);

        long before = principalOf(memberId, loan);
        loan.updateBaseline(request.principal(), asOf);
        loanRepository.flush();
        return new LoanDtos.BaselineChange(before, principalOf(memberId, loan));
    }

    /**
     * 금리 변경. <b>적용일 이후 회차만</b> 새 금리로 계산한다 — 지난 이자는 이미 원장에 있다.
     * 같은 적용일을 다시 적으면 그 행의 금리를 고친다: 잘못 적은 금리를 행 두 개로 남기지 않는다.
     */
    @Transactional
    public List<LoanDtos.Rate> changeRate(Long memberId, LoanDtos.RateRequest request) {
        if (request.assetId() == null) {
            throw new CustomException(ErrorCode.LEDGER_LOAN_INVALID);
        }
        requireLoanAsset(memberId, request.assetId());
        requireLoan(request.assetId());
        if (request.effectiveFrom() == null || !validRate(request.annualRate())) {
            throw new CustomException(ErrorCode.LEDGER_LOAN_INVALID);
        }
        BigDecimal rate = normalized(request.annualRate());
        rateRepository.findByAssetIdAndEffectiveFrom(request.assetId(), request.effectiveFrom())
                .ifPresentOrElse(
                        row -> row.updateAnnualRate(rate),
                        () -> rateRepository.save(new LedgerRateHistory(
                                request.assetId(), request.effectiveFrom(), rate)));
        return rateRepository.findAllByAssetIdOrderByEffectiveFromDesc(request.assetId()).stream()
                .map(LoanDtos.Rate::of)
                .toList();
    }

    /**
     * 대출별 잔여 원금 재료. 잔액·부채를 말하는 곳이 {@code LedgerBalances}에 넘긴다.
     *
     * @param until 이 날까지의 거래만 센다. 지금이면 오늘, 지난 달의 순자산이면 그 달의 끝
     */
    @Transactional(readOnly = true)
    public List<LoanPrincipal> principals(Long memberId, LocalDate until) {
        return loanRepository.sumPrincipalUpTo(memberId, until);
    }

    /** 이 계좌에서 원금이 빠지는 대출이 있는가. 있으면 그 계좌는 지울 수 없다. */
    @Transactional(readOnly = true)
    public boolean isPaymentAccount(Long assetId) {
        return loanRepository.existsByPaymentAssetId(assetId);
    }

    /** 대출 자산을 지울 때 속성과 금리 이력을 함께 지운다. 거래가 붙은 대출은 여기까지 오지 않는다. */
    @Transactional
    public void forget(Long assetId) {
        rateRepository.deleteAllByAssetId(assetId);
        loanRepository.findById(assetId).ifPresent(loanRepository::delete);
    }

    // --- 내부 ---

    private long principalOf(Long memberId, LedgerLoan loan) {
        return principals(memberId, clock.today()).stream()
                .filter(row -> row.getAssetId().equals(loan.getAssetId()))
                .mapToLong(LoanPrincipal::principal)
                .findFirst()
                .orElse(loan.getBaselinePrincipal());
    }

    /** 오늘 적용되는 금리. 최신이 위인 목록에서 적용일이 오늘 이전인 첫 행이다. */
    private BigDecimal currentRate(List<LedgerRateHistory> ratesNewestFirst, LocalDate today) {
        for (LedgerRateHistory row : ratesNewestFirst) {
            if (!row.getEffectiveFrom().isAfter(today)) {
                return row.getAnnualRate();
            }
        }
        // 실행일이 아직 안 왔다 — 약정 금리(가장 이른 행)를 보여준다.
        return ratesNewestFirst.isEmpty() ? null : ratesNewestFirst.getLast().getAnnualRate();
    }

    /**
     * 대출 속성 검사(LDG-ERR-041). 출금 계좌는 <b>잔액을 갖는 자산만</b> — 카드에서 원금이
     * 빠질 수는 없고, 그런 대출은 상환 처리에서 이체를 만들 곳이 없다.
     */
    private void validateTerms(Long memberId, LoanDtos.Open request) {
        if (request.repaymentMethod() == null
                || request.paymentAssetId() == null
                || request.startedOn() == null
                || request.maturityDate() == null
                || !request.maturityDate().isAfter(request.startedOn())
                || !validDay(request.paymentDay())
                || !validRate(request.annualRate())
                || (request.originalPrincipal() != null && request.originalPrincipal() < 0)) {
            throw new CustomException(ErrorCode.LEDGER_LOAN_INVALID);
        }
        LedgerAsset payment = assetRepository.findByIdAndMemberId(request.paymentAssetId(), memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_ASSET_NOT_FOUND));
        if (!payment.getType().holdsBalance()) {
            throw new CustomException(ErrorCode.LEDGER_LOAN_INVALID);
        }
    }

    /** 음수 원금·미래 기준일은 은행 앱이 보여줄 수 없는 값이다(LDG-ERR-045). */
    private void validateBaseline(long principal, LocalDate asOf, LocalDate today) {
        if (principal < 0 || asOf.isAfter(today)) {
            throw new CustomException(ErrorCode.LEDGER_BASELINE_INVALID);
        }
    }

    /** 29~31일은 없는 달이 있어 받지 않는다. 말일은 99로 적는다. */
    private boolean validDay(Integer day) {
        return day != null
                && ((day >= 1 && day <= MAX_PAYMENT_DAY) || day == LedgerSettings.LAST_DAY_OF_MONTH);
    }

    private boolean validRate(BigDecimal rate) {
        return rate != null && rate.signum() >= 0 && rate.compareTo(MAX_RATE) <= 0;
    }

    /** 소수 넷째 자리 이하는 DB가 담지 못한다. 조용히 잘리기 전에 반올림해 둔다. */
    private BigDecimal normalized(BigDecimal rate) {
        return rate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** 대출이 아니거나 남의 자산이면 없는 것으로 본다 — 403이면 「그 id의 자산은 있다」가 새어나간다. */
    private LedgerAsset requireLoanAsset(Long memberId, Long assetId) {
        return assetRepository.findByIdAndMemberId(assetId, memberId)
                .filter(LedgerAsset::isLoan)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_ASSET_NOT_FOUND));
    }

    private LedgerLoan requireLoan(Long assetId) {
        return loanRepository.findById(assetId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_ASSET_NOT_FOUND));
    }
}
