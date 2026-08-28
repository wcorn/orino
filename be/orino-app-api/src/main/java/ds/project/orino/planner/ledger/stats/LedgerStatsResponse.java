package ds.project.orino.planner.ledger.stats;

import java.time.LocalDate;
import java.util.List;

/**
 * 카테고리 통계(`LDG-081`).
 *
 * <p><b>관점 전환(소비/청구)은 v2다.</b> 여기에 토글이 없는 것은 누락이 아니다 — 할부가 없으면
 * 두 관점이 같은 값이라, 토글을 그려 두면 아무 일도 안 하는 것처럼 보인다.
 *
 * @param total      이번 구간에 <b>쓴 돈</b>. 이체는 들어가지 않고 환불은 깎여 있다
 * @param byCategory 많이 쓴 순. 미분류도 한 칸을 차지한다 — 빼면 정리하지 않는다
 */
public record LedgerStatsResponse(
        Period period,
        long total,
        List<CategoryStat> byCategory,
        Comparison comparison
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
}
