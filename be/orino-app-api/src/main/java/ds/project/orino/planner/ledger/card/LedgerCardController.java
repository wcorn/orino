package ds.project.orino.planner.ledger.card;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerAssetType;
import ds.project.orino.domain.planner.ledger.entity.LedgerStatement;
import ds.project.orino.domain.planner.ledger.repository.LedgerAssetRepository;
import ds.project.orino.planner.ledger.common.LedgerBalances;
import ds.project.orino.planner.ledger.common.LedgerClock;
import ds.project.orino.planner.ledger.transaction.dto.TransactionView;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 카드·청구서 API(확정 명세 §7).
 *
 * <p><b>대금을 자동으로 기록하는 엔드포인트가 없다.</b> 결제는 사람이 {@code /pay}를 눌러야
 * 일어난다 — 잔고 부족·리볼빙·선결제·연회비 때문에 실제 출금액을 앱이 알 수 없기 때문이다(§7.2).
 * 모르는 걸 아는 척 적어두면 원장이 조용히 틀어진다.
 */
@RestController
@RequestMapping("/api/ledger")
public class LedgerCardController {

    private final LedgerStatementService statementService;
    private final LedgerInstallmentService installmentService;
    private final LedgerUsageGoalService usageGoalService;
    private final LedgerAssetRepository assetRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerClock clock;

    public LedgerCardController(LedgerStatementService statementService,
                                LedgerInstallmentService installmentService,
                                LedgerUsageGoalService usageGoalService,
                                LedgerAssetRepository assetRepository,
                                LedgerTransactionRepository transactionRepository,
                                LedgerClock clock) {
        this.statementService = statementService;
        this.installmentService = installmentService;
        this.usageGoalService = usageGoalService;
        this.assetRepository = assetRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    /** 카드 목록. 사이드바가 가리키는 곳이자 청구서로 들어가는 입구다(D-11). */
    @GetMapping("/cards")
    @Transactional(readOnly = true)
    public ApiResponse<LedgerCardDtos.CardListResponse> cards(
            @AuthenticationPrincipal Long memberId) {
        List<LedgerAsset> assets =
                assetRepository.findAllByMemberIdOrderByDisplayOrderAscIdAsc(memberId);
        LedgerBalances balances = LedgerBalances.of(assets,
                transactionRepository.sumConfirmedByAssetAndType(
                        memberId, LedgerTransactionStatus.CONFIRMED),
                transactionRepository.sumConfirmedByCounterAsset(
                        memberId, LedgerTransactionStatus.CONFIRMED));

        List<LedgerCardDtos.CardView> cards = new ArrayList<>();
        for (LedgerAsset asset : assets) {
            if (asset.getType() != LedgerAssetType.CREDIT_CARD) {
                continue;
            }
            cards.add(new LedgerCardDtos.CardView(
                    asset.getId(), asset.getName(), asset.getAccountLast4(),
                    asset.getCycleStartDay(), asset.getCycleCloseDay(), asset.getPaymentDay(),
                    asset.getPaymentAssetId(), nameOf(assets, asset.getPaymentAssetId()),
                    asset.getCreditLimit(), asset.hasBillingCycle(),
                    balances.unpaidOf(asset.getId()) == null ? 0 : balances.unpaidOf(asset.getId()),
                    currentStatementOf(memberId, asset),
                    usageGoalService.progressOf(asset)));
        }
        return ApiResponse.success(new LedgerCardDtos.CardListResponse(
                cards, installmentService.outstandingPrincipal(memberId)));
    }

    /** 사이클 등록·수정. 셋(시작·마감·결제일)이 갖춰져야 청구서가 만들어진다. */
    @PatchMapping("/cards/{id}/cycle")
    @Transactional
    public ApiResponse<Void> updateCycle(@AuthenticationPrincipal Long memberId,
                                         @PathVariable Long id,
                                         @Valid @RequestBody LedgerCardDtos.CycleRequest request) {
        LedgerAsset card = assetRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_ASSET_NOT_FOUND));
        if (card.getType() != LedgerAssetType.CREDIT_CARD) {
            throw new CustomException(ErrorCode.LEDGER_NOT_A_CREDIT_CARD);
        }
        card.updateBillingCycle(request.cycleStartDay(), request.cycleCloseDay(),
                request.paymentDay(), request.paymentAssetId(), request.creditLimit());
        return ApiResponse.success();
    }

    /**
     * 실적 조건 등록·해제(§7.6).
     *
     * <p>기준을 <b>카드마다</b> 받는다 — 전역 설정으로 두면 카드 두 장에서 한쪽이 반드시 틀린다.
     */
    @PatchMapping("/cards/{id}/usage-goal")
    @Transactional
    public ApiResponse<Void> updateUsageGoal(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody LedgerCardDtos.UsageGoalRequest request) {
        LedgerAsset card = assetRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_ASSET_NOT_FOUND));
        if (card.getType() != LedgerAssetType.CREDIT_CARD) {
            throw new CustomException(ErrorCode.LEDGER_NOT_A_CREDIT_CARD);
        }
        card.updateUsageGoal(request.goalAmount(), request.basis());
        return ApiResponse.success();
    }

    @GetMapping("/cards/{id}/statements")
    @Transactional(readOnly = true)
    public ApiResponse<List<LedgerCardDtos.StatementView>> statements(
            @AuthenticationPrincipal Long memberId, @PathVariable Long id) {
        return ApiResponse.success(viewsOf(statementService.statementsOf(memberId, id)));
    }

    /** 청구서 한 장 — <b>산식을 그대로</b> 내려준다. */
    @GetMapping("/statements/{id}")
    @Transactional(readOnly = true)
    public ApiResponse<LedgerCardDtos.StatementView> statement(
            @AuthenticationPrincipal Long memberId, @PathVariable Long id) {
        LedgerStatement statement = statementService.require(memberId, id);
        return ApiResponse.success(LedgerCardDtos.StatementView.of(
                statement, statementService.breakdownOf(statement), clock.today()));
    }

    @GetMapping("/statements/{id}/transactions")
    public ApiResponse<List<TransactionView>> statementTransactions(
            @AuthenticationPrincipal Long memberId, @PathVariable Long id) {
        return ApiResponse.success(statementService.transactionsOf(memberId, id));
    }

    /**
     * 결제 처리. 이체 INSERT + 청구서 UPDATE + (부분 납부면) 이월이 <b>한 트랜잭션</b>이다.
     *
     * <p>여기서 만들어지는 거래는 반드시 이체다 — 지출로 계상되지 않는다(§7.3).
     */
    @PostMapping("/statements/{id}/pay")
    @Transactional
    public ApiResponse<LedgerCardDtos.StatementView> pay(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody LedgerStatementPayRequest request) {
        LedgerStatement statement = statementService.pay(memberId, id, request);
        return ApiResponse.success(LedgerCardDtos.StatementView.of(
                statement, statementService.breakdownOf(statement), clock.today()));
    }

    /** 차액 조정·수수료·할인. 차액은 <b>원인 카테고리와 함께</b> 남는다. */
    @PostMapping("/statements/{id}/adjust")
    @Transactional
    public ApiResponse<LedgerCardDtos.StatementView> adjust(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody LedgerStatementAdjustRequest request) {
        LedgerStatement statement = statementService.adjust(memberId, id, request);
        return ApiResponse.success(LedgerCardDtos.StatementView.of(
                statement, statementService.breakdownOf(statement), clock.today()));
    }

    /** 할부 중도 상환·취소. 아직 청구되지 않은 회차만 정리한다. */
    @PostMapping("/installments/{id}/cancel")
    public ApiResponse<Void> cancelInstallment(@AuthenticationPrincipal Long memberId,
                                               @PathVariable Long id) {
        installmentService.cancel(memberId, id);
        return ApiResponse.success();
    }

    private List<LedgerCardDtos.StatementView> viewsOf(List<LedgerStatement> statements) {
        LocalDate today = clock.today();
        List<LedgerCardDtos.StatementView> views = new ArrayList<>();
        for (LedgerStatement statement : statements) {
            views.add(LedgerCardDtos.StatementView.of(
                    statement, statementService.breakdownOf(statement), today));
        }
        return views;
    }

    /** 지금 열려 있는(또는 가장 최근) 청구서. 「다음 결제일에 얼마 빠지나」의 답이다. */
    private LedgerCardDtos.StatementView currentStatementOf(Long memberId, LedgerAsset card) {
        List<LedgerStatement> statements = statementService.statementsOf(memberId, card.getId());
        if (statements.isEmpty()) {
            return null;
        }
        LedgerStatement latest = statements.get(0);
        return LedgerCardDtos.StatementView.of(
                latest, statementService.breakdownOf(latest), clock.today());
    }

    private String nameOf(List<LedgerAsset> assets, Long assetId) {
        if (assetId == null) {
            return null;
        }
        return assets.stream()
                .filter(asset -> asset.getId().equals(assetId))
                .map(LedgerAsset::getName)
                .findFirst()
                .orElse(null);
    }
}
