package ds.project.orino.planner.ledger.subscription;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerHousingSubscription;
import ds.project.orino.domain.planner.ledger.repository.LedgerAssetRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerHousingSubscriptionRepository;
import ds.project.orino.domain.planner.ledger.repository.LedgerTransactionRepository;
import ds.project.orino.planner.ledger.common.LedgerClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 청약 인정 현황(`LDG-007`). <b>인정 회차와 금액의 정본은 청약홈이다</b> — 여기서는 그 사이를
 * 원장으로 이어 추정하고, 어긋나면 기준값을 다시 맞춘다(덧붙인 명세 §3).
 */
@Service
public class LedgerSubscriptionService {

    private final LedgerAssetRepository assetRepository;
    private final LedgerHousingSubscriptionRepository subscriptionRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerClock clock;

    public LedgerSubscriptionService(LedgerAssetRepository assetRepository,
                                     LedgerHousingSubscriptionRepository subscriptionRepository,
                                     LedgerTransactionRepository transactionRepository,
                                     LedgerClock clock) {
        this.assetRepository = assetRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    /** 기준값이 없으면 추정을 그리지 않는다 — 가입일부터 센 틀린 숫자보다 빈칸이 낫다. */
    @Transactional(readOnly = true)
    public SubscriptionDtos.Response get(Long memberId, Long assetId) {
        requireSubscription(memberId, assetId);
        YearMonth currentMonth = YearMonth.from(clock.today());
        long monthlyCap = LedgerSubscriptionEstimator.monthlyCap(currentMonth);

        Optional<LedgerSubscriptionEstimator.Baseline> baseline = baselineOf(assetId);
        if (baseline.isEmpty()) {
            return new SubscriptionDtos.Response(assetId, null, null, List.of(), monthlyCap);
        }
        return SubscriptionDtos.Response.of(assetId, baseline.get(),
                estimate(memberId, assetId, baseline.get(), currentMonth), monthlyCap);
    }

    /**
     * 청약홈 값으로 기준값을 다시 맞춘다. <b>바꾸기 직전의 추정을 함께 돌려준다</b> — 화면은
     * 그 차이를 한 번 보여준다. 조용히 덮으면 추정이 틀렸다는 사실도 함께 사라진다.
     */
    @Transactional
    public SubscriptionDtos.BaselineChange updateBaseline(Long memberId, Long assetId,
                                                          SubscriptionDtos.BaselineRequest request) {
        requireSubscription(memberId, assetId);
        YearMonth currentMonth = YearMonth.from(clock.today());
        LedgerSubscriptionEstimator.Baseline next = validBaseline(request, currentMonth);

        SubscriptionDtos.Totals before = baselineOf(assetId)
                .map(baseline -> SubscriptionDtos.Totals.of(
                        estimate(memberId, assetId, baseline, currentMonth)))
                .orElse(null);

        LedgerHousingSubscription row = subscriptionRepository.findById(assetId)
                .orElseGet(() -> new LedgerHousingSubscription(assetId));
        row.updateBaseline(next.count(), next.amount(), next.throughMonth(), clock.now());
        subscriptionRepository.save(row);

        return new SubscriptionDtos.BaselineChange(before,
                SubscriptionDtos.Totals.of(estimate(memberId, assetId, next, currentMonth)));
    }

    /**
     * 자산 목록 부제(「40회 인정 (추정)」)에 쓸 인정 회차.
     *
     * @return 청약이 아니거나 기준값이 없으면 {@code null}. 0회와 「모른다」는 다르다
     */
    @Transactional(readOnly = true)
    public Integer estimatedCount(Long memberId, LedgerAsset asset) {
        if (!asset.isHousingSubscription()) {
            return null;
        }
        YearMonth currentMonth = YearMonth.from(clock.today());
        return baselineOf(asset.getId())
                .map(baseline -> estimate(memberId, asset.getId(), baseline, currentMonth).count())
                .orElse(null);
    }

    /**
     * 자산을 지울 때 기준값도 함께 지운다. 기준값은 원장이 아니라 외부 조회 값이라(D-18)
     * 삭제를 막을 근거가 아니다.
     */
    @Transactional
    public void forget(Long assetId) {
        subscriptionRepository.findById(assetId).ifPresent(subscriptionRepository::delete);
    }

    // --- 내부 ---

    /**
     * 기준 월 다음 달부터의 확정 이체 입금. 수입(이자)·조정 거래는 세지 않는다 — 이체가 아니라서
     * 질의에서 빠진다.
     */
    private LedgerSubscriptionEstimator.Result estimate(Long memberId, Long assetId,
                                                        LedgerSubscriptionEstimator.Baseline baseline,
                                                        YearMonth currentMonth) {
        Map<YearMonth, Long> deposits = new HashMap<>();
        for (LedgerTransactionRepository.DailyTotal day : transactionRepository.sumConfirmedTransfersIntoByDay(
                memberId, assetId, baseline.throughMonth().plusMonths(1).atDay(1))) {
            deposits.merge(YearMonth.from(day.getDate()), day.getTotal(), Long::sum);
        }
        return LedgerSubscriptionEstimator.estimate(baseline, deposits, currentMonth);
    }

    private Optional<LedgerSubscriptionEstimator.Baseline> baselineOf(Long assetId) {
        return subscriptionRepository.findById(assetId)
                .filter(LedgerHousingSubscription::hasBaseline)
                .map(row -> new LedgerSubscriptionEstimator.Baseline(
                        row.getBaselineCount(), row.getBaselineAmount(),
                        row.getBaselineThroughMonth()));
    }

    /**
     * 셋 다 있어야 하고, 음수가 아니고, 미래 월이 아니어야 한다(LDG-ERR-045). 미래 월은 청약홈이
     * 보여줄 수 없는 값이다 — 받아 두면 이번 달까지의 입금이 통째로 빠진다.
     */
    private LedgerSubscriptionEstimator.Baseline validBaseline(SubscriptionDtos.BaselineRequest request,
                                                               YearMonth currentMonth) {
        if (request.count() == null || request.amount() == null || request.throughMonth() == null
                || request.count() < 0 || request.amount() < 0
                || request.throughMonth().isAfter(currentMonth)) {
            throw new CustomException(ErrorCode.LEDGER_BASELINE_INVALID);
        }
        return new LedgerSubscriptionEstimator.Baseline(
                request.count(), request.amount(), request.throughMonth());
    }

    /** 청약 종류가 붙은 예·적금만. 일반 예·적금에 인정 현황을 그리면 없는 숫자가 생긴다. */
    private LedgerAsset requireSubscription(Long memberId, Long assetId) {
        LedgerAsset asset = assetRepository.findByIdAndMemberId(assetId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_ASSET_NOT_FOUND));
        if (!asset.isHousingSubscription()) {
            throw new CustomException(ErrorCode.LEDGER_SAVINGS_KIND_MISMATCH);
        }
        return asset;
    }
}
