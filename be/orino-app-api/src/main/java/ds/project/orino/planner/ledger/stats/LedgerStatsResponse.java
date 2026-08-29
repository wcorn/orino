package ds.project.orino.planner.ledger.stats;

import ds.project.orino.domain.planner.ledger.entity.LedgerPerspective;

import java.time.LocalDate;
import java.util.List;

/**
 * 통계(`LDG-081`·`LDG-082`·`LDG-086`).
 *
 * <p><b>관점 전환은 v2에서 열렸다.</b> 할부가 없으면 두 관점이 같은 값이라 v1에서는 토글이
 * 아무 일도 안 하는 것처럼 보였다 — 지금은 할부가 있고, 두 값이 벌어지는 <b>이유까지</b>
 * 서버가 계산해 내려준다.
 *
 * @param total      이번 구간에 <b>쓴 돈</b>. 이체는 들어가지 않고 환불은 깎여 있다
 * @param byCategory 많이 쓴 순. 미분류도 한 칸을 차지한다 — 빼면 정리하지 않는다
 */
public record LedgerStatsResponse(
        Period period,
        LedgerPerspective perspective,
        long total,
        List<CategoryStat> byCategory,
        List<AssetStat> byAsset,
        FixedVsVariable fixedVsVariable,
        List<MonthlyPoint> monthly,
        Settlement settlement,
        Comparison comparison,
        PerspectiveDiff perspectiveDiff
) {

    public record Period(LocalDate start, LocalDate end, String label) {
    }

    /**
     * @param categoryId {@code null}이면 미분류
     * @param share      전체 대비 비율(0~1). 화면이 다시 나누지 않도록 서버가 계산해 준다
     */
    public record CategoryStat(
            Long categoryId,
            String categoryName,
            long amount,
            long count,
            double share
    ) {
    }

    /** 자산별 지출(`LDG-082`). 「어느 카드로 많이 쓰나」에 답한다. */
    public record AssetStat(
            Long assetId,
            String assetName,
            long amount,
            double share
    ) {
    }

    /**
     * 고정비 대 변동비.
     *
     * <p>이 구분이 있어야 <b>절약 여지가 어디 있는지</b>가 보인다 — 고정비 비중이 계속 오르면
     * 커피를 줄일 게 아니라 정기 항목을 정리해야 하고, 변동비만 보는 화면에서는 그 판단이
     * 절대 나오지 않는다.
     *
     * @param unclassified 아직 속성을 안 정한 카테고리의 지출. <b>0으로 숨기지 않는다</b> —
     *                     분류가 덜 됐다는 사실 자체가 이 화면에서 읽혀야 한다
     */
    public record FixedVsVariable(long fixed, long variable, long unclassified) {
    }

    /** 월별 한 점. 연간 결산 막대와 고정/변동 추이가 같은 배열을 읽는다. */
    public record MonthlyPoint(
            String month,
            long expense,
            long income,
            long fixed,
            long variable,
            /**
             * 속성을 안 정한 카테고리의 지출.
             *
             * <p><b>빼면 막대가 지출보다 짧아진다</b> — 셋을 더해야 {@code expense}가 되고,
             * 그래야 「왜 이 달만 막대가 짧지」라는 질문이 안 생긴다.
             */
            long unclassified,
            /** 그 달 말의 순자산. 아직 오지 않은 달은 {@code null}이다. */
            Long netWorth
    ) {
    }

    /**
     * 연간 결산.
     *
     * @param savingRate 저축률(0~1). 수입이 없으면 {@code null}이다 — 0으로 두면
     *                   「하나도 못 모았다」로 읽히는데 사실은 「셀 수 없다」다
     */
    public record Settlement(
            int year,
            long income,
            long expense,
            Double savingRate,
            String highestMonth,
            String lowestMonth
    ) {
    }

    /**
     * 기간 비교. <b>지난 구간</b>과 <b>작년 같은 구간</b>이다.
     *
     * @param diff 이번 − 그때. 양수면 더 썼다는 뜻이다
     */
    public record Comparison(
            Bucket previousPeriod,
            Bucket previousYear
    ) {

        public record Bucket(LocalDate start, LocalDate end, long total, long diff) {
        }
    }

    /**
     * 다른 관점으로 보면 얼마가 달라지는가.
     *
     * <p><b>화면이 계산하게 두지 않는다.</b> 두 곳에서 세면 어느 쪽이 맞는지 알 수 없다(D-13).
     *
     * @param diff   다른 관점 − 지금 관점. 음수면 그쪽이 더 적게 잡힌다
     * @param reason 왜 벌어지나. 벌어지지 않으면 {@code null}이다 — 이유 없는 안내를
     *               그리지 않기 위해서다
     */
    public record PerspectiveDiff(
            LedgerPerspective other,
            long otherTotal,
            long diff,
            String reason
    ) {
    }
}
