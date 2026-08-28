package ds.project.orino.planner.ledger.summary;

import java.time.LocalDate;

/**
 * {@code /select} 가계부 카드와 대시보드 상단이 함께 쓰는 요약.
 *
 * <p><b>v1.5에서 채워지는 값은 지금 {@code null}이다</b>(확정 명세 §15.1). 0으로 채우지
 * 않는다 — 「미납이 없다」와 「아직 셀 수 없다」는 다르고, 화면은 {@code null}인 줄을
 * 아예 그리지 않는 것으로 그 차이를 지킨다.
 *
 * @param monthEstimate      이번 달 예상 지출(이미 쓴 것 + 예정)
 * @param monthSpent         이미 쓴 돈. 카드 대금 납부는 들어가지 않는다
 * @param monthScheduled     남은 예정 지출
 * @param uncategorizedCount 정리할 내역
 * @param monthEndBalance    월말 예상 잔액. <b>v1.5</b> — 카드 청구서가 있어야 계산된다
 * @param remainingOutflow   앞으로 나갈 돈. <b>v1.5</b>
 * @param overdueCount       미납 건수. <b>v1.5</b> — 정기 항목이 있어야 「빠지지 않았다」를 안다
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
