package ds.project.orino.planner.ledger.summary;

import java.time.LocalDate;

/**
 * {@code /select} 가계부 카드와 대시보드 상단이 함께 쓰는 요약.
 *
 * <p>뒤의 셋은 v1에서 {@code null}이었다 — 「미납이 없다」와 「아직 셀 수 없다」는 다르고,
 * 0으로 채우면 화면이 「없음」을 그렸을 것이다. <b>v1.5(#1264)에서 카드 청구서와 정기 항목이
 * 생겨 셀 수 있게 됐다.</b> 타입은 {@code Long}으로 남는다 — 값이 없는 상태가 다시 생길 수
 * 있고, 그때 0으로 둘러대지 않기 위해서다.
 *
 * @param monthEstimate      이번 달 예상 지출(이미 쓴 것 + 예정)
 * @param monthSpent         이미 쓴 돈. 카드 대금 납부는 들어가지 않는다
 * @param monthScheduled     남은 예정 지출
 * @param uncategorizedCount 정리할 내역
 * @param monthEndBalance    월말 예상 잔액. 저축을 뺀 「쓸 수 있는 돈」 기준이다
 * @param remainingOutflow   앞으로 나갈 돈. 지출과 이체를 함께 센다
 * @param overdueCount       미납 건수. 정기 회차와 카드 청구서 두 곳에서 온다
 */
public record LedgerSummaryResponse(
        long monthEstimate,
        long monthSpent,
        long monthScheduled,
        long uncategorizedCount,
        Long monthEndBalance,
        Long remainingOutflow,
        Long overdueCount,
        Period period
) {

    /** 월 시작일 설정이 반영된 이번 달 구간. 1일이 아닐 수 있다(급여일 기준이면 25일). */
    public record Period(LocalDate start, LocalDate end) {
    }
}
