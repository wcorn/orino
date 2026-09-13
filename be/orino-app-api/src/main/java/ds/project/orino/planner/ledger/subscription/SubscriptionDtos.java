package ds.project.orino.planner.ledger.subscription;

import java.time.YearMonth;
import java.util.List;

/** 청약 인정 현황 요청·응답 묶음(API §10.2). */
public final class SubscriptionDtos {

    private SubscriptionDtos() {
    }

    /**
     * 인정 현황.
     *
     * @param baseline   청약홈 기준값. 없으면 {@code null}이고 추정도 {@code null}이다 —
     *                   <b>가입일부터 세서 채우지 않는다</b>
     * @param months     기준 월 다음 달부터 이번 달까지. 오래된 달이 앞이다
     * @param monthlyCap 이번 달의 월 인정 상한
     */
    public record Response(
            Long assetId,
            BaselineView baseline,
            EstimateView estimate,
            List<MonthView> months,
            long monthlyCap
    ) {

        static Response of(Long assetId, LedgerSubscriptionEstimator.Baseline baseline,
                           LedgerSubscriptionEstimator.Result result, long monthlyCap) {
            return new Response(
                    assetId,
                    new BaselineView(baseline.count(), baseline.amount(), baseline.throughMonth()),
                    new EstimateView(result.count(), result.amount(), true),
                    result.months().stream()
                            .map(month -> new MonthView(month.month(), month.deposited(),
                                    month.recognized(), month.flag()))
                            .toList(),
                    monthlyCap);
        }
    }

    public record BaselineView(int count, long amount, YearMonth throughMonth) {
    }

    /** @param isEstimate 늘 참이다. 화면 어디에서도 단정하지 않게 값 자체가 말한다 */
    public record EstimateView(int count, long amount, boolean isEstimate) {
    }

    public record MonthView(
            YearMonth month,
            long deposited,
            long recognized,
            LedgerSubscriptionEstimator.Flag flag
    ) {
    }

    /**
     * 기준값 다시 맞추기. 셋 다 있어야 한다(LDG-ERR-045) — 빈 값을 bean validation에 맡기지
     * 않는 이유는 「일부만 왔다」도 같은 코드로 말해야 해서다.
     */
    public record BaselineRequest(Integer count, Long amount, YearMonth throughMonth) {
    }

    /**
     * @param before 바꾸기 <b>직전의 추정</b>. 처음 적으면 {@code null}이다. 기준값을 조용히
     *               덮으면 추정이 틀렸다는 사실도 함께 사라진다
     * @param after  새 기준값으로 다시 센 추정
     */
    public record BaselineChange(Totals before, Totals after) {
    }

    public record Totals(int count, long amount) {

        static Totals of(LedgerSubscriptionEstimator.Result result) {
            return new Totals(result.count(), result.amount());
        }
    }
}
