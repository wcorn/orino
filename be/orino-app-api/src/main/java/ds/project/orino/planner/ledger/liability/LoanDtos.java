package ds.project.orino.planner.ledger.liability;

import ds.project.orino.domain.planner.ledger.entity.LedgerBusinessDayPolicy;
import ds.project.orino.domain.planner.ledger.entity.LedgerRateHistory;
import ds.project.orino.domain.planner.ledger.entity.LedgerRepaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 대출 요청·응답 묶음(API §10.1·§10.3). */
public final class LoanDtos {

    private LoanDtos() {
    }

    /**
     * 자산 생성에 붙는 대출 블록. 틀리면 {@code LDG-ERR-041}, 기준값이 틀리면 {@code LDG-ERR-045}.
     *
     * @param paymentAssetId    출금 계좌. <b>잔액을 갖는 자산만</b> — 카드에서 원금이 빠질 수는 없다
     * @param paymentDay        매월 N일. 1~28 또는 99(말일)
     * @param businessDayPolicy 없으면 {@code AS_IS}
     * @param annualRate        연 %. 금리 이력의 <b>첫 행</b>이 되고 적용일은 실행일이다
     * @param baselinePrincipal 은행 앱의 <b>지금 잔여 원금</b>. 새로 받는 대출이면 0으로 두고 실행을
     *                          이체로 적는다. 없으면 0
     * @param baselineAsOf      기준일. 이 날까지의 실행·상환은 기준 원금에 들어 있다. 없으면 오늘
     */
    public record Open(
            LedgerRepaymentMethod repaymentMethod,
            Long paymentAssetId,
            Integer paymentDay,
            LedgerBusinessDayPolicy businessDayPolicy,
            LocalDate startedOn,
            LocalDate maturityDate,
            Long originalPrincipal,
            BigDecimal annualRate,
            Long baselinePrincipal,
            LocalDate baselineAsOf
    ) {
    }

    /**
     * 대출 상세.
     *
     * @param principalRemaining 기준 원금 + 기준일 이후 이체. <b>저장된 값이 아니다</b>(D-8)
     * @param currentRate        오늘 적용되는 연 %. 적용일이 모두 미래면 가장 이른 행이다
     * @param rates              금리 이력. 최신이 위다
     */
    public record Response(
            Long assetId,
            String name,
            long principalRemaining,
            LedgerRepaymentMethod repaymentMethod,
            Long paymentAssetId,
            String paymentAssetName,
            int paymentDay,
            LedgerBusinessDayPolicy businessDayPolicy,
            LocalDate startedOn,
            LocalDate maturityDate,
            Long originalPrincipal,
            Baseline baseline,
            BigDecimal currentRate,
            List<Rate> rates
    ) {
    }

    public record Baseline(long principal, LocalDate asOf) {
    }

    public record Rate(LocalDate effectiveFrom, BigDecimal annualRate) {

        static Rate of(LedgerRateHistory row) {
            return new Rate(row.getEffectiveFrom(), row.getAnnualRate());
        }
    }

    /** 기준 원금 다시 맞추기. {@code asOf}가 없으면 오늘이다. */
    public record BaselineRequest(Long principal, LocalDate asOf) {
    }

    /**
     * @param before 바꾸기 <b>직전의 원장 추정</b> 잔여 원금. 화면은 「원장 추정 → 은행」 차이를
     *               한 번 보여준다 — 조용히 덮으면 어긋났다는 사실도 함께 사라진다
     * @param after  새 기준으로 다시 센 잔여 원금
     */
    public record BaselineChange(long before, long after) {
    }

    /** 금리 변경. 대출·마이너스통장 공용이다(§10.3). 같은 적용일이면 그 행의 금리를 고친다. */
    public record RateRequest(Long assetId, LocalDate effectiveFrom, BigDecimal annualRate) {
    }
}
