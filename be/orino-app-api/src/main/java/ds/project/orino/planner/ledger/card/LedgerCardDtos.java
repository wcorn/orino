package ds.project.orino.planner.ledger.card;

import ds.project.orino.domain.planner.ledger.entity.LedgerStatement;
import ds.project.orino.domain.planner.ledger.entity.LedgerStatementStatus;
import ds.project.orino.domain.planner.ledger.entity.LedgerUsageGoalBasis;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;
import java.util.List;

/** 카드·청구서 응답 묶음. */
public final class LedgerCardDtos {

    private LedgerCardDtos() {
    }

    /** 사이클 등록. 셋이 함께 있어야 청구서를 만들 수 있다. */
    public record CycleRequest(
            @Min(1) @Max(99) Integer cycleStartDay,
            @Min(1) @Max(99) Integer cycleCloseDay,
            @Min(1) @Max(99) Integer paymentDay,
            Long paymentAssetId,
            Long creditLimit
    ) {
    }

    /** 실적 조건. 금액과 기준은 함께 있거나 함께 없다 — 하나만 있으면 셀 수가 없다. */
    public record UsageGoalRequest(
            Long goalAmount,
            LedgerUsageGoalBasis basis
    ) {
    }

    /**
     * 카드 한 장.
     *
     * @param unpaidAmount 미결제 사용액. <b>잔액이 아니라 부채</b>다
     * @param hasCycle     사이클이 등록됐는지. 없으면 청구서가 만들어지지 않는다
     */
    public record CardView(
            Long id,
            String name,
            String accountLast4,
            Integer cycleStartDay,
            Integer cycleCloseDay,
            Integer paymentDay,
            Long paymentAssetId,
            String paymentAssetName,
            Long creditLimit,
            boolean hasCycle,
            long unpaidAmount,
            StatementView currentStatement,
            /** 실적을 안 걸어 둔 카드는 {@code null}이다 — 0%로 그리면 「못 채웠다」로 읽힌다. */
            UsageGoalView usageGoal
    ) {
    }

    /**
     * 청구서 한 장. <b>산식을 그대로 내려준다</b> — 합계만 주면 카드사 앱과 다를 때
     * 어디가 다른지 알 방법이 없다(확정 명세 §7.4).
     *
     * @param overdue 결제일이 지났는데 아직 안 냈다. <b>저장값이 아니라 판정</b>이다
     */
    public record StatementView(
            Long id,
            Long cardAssetId,
            LocalDate cycleStart,
            LocalDate cycleEnd,
            LocalDate paymentDate,
            LedgerStatementStatus status,
            boolean overdue,
            LedgerStatementBreakdown breakdown,
            LocalDate paidOn,
            Long carriedToStatementId
    ) {

        public static StatementView of(LedgerStatement statement,
                                       LedgerStatementBreakdown breakdown,
                                       LocalDate today) {
            return new StatementView(
                    statement.getId(), statement.getCardAssetId(),
                    statement.getCycleStart(), statement.getCycleEnd(),
                    statement.getPaymentDate(), statement.getStatus(),
                    statement.isOverdueOn(today), breakdown,
                    statement.getPaidOn(), statement.getCarriedToStatementId());
        }
    }

    /**
     * 카드 실적 진행(§7.6).
     *
     * @param basis     <b>카드 속성</b>이지 전역 설정이 아니다. 화면이 「승인 기준」 배지로 적는다
     * @param remaining 조건까지 남은 금액. 「88,000원 더 쓰면 충족」이 이 값이다
     */
    public record UsageGoalView(
            long goalAmount,
            LedgerUsageGoalBasis basis,
            long counted,
            long remaining,
            boolean achieved,
            String month
    ) {
    }

    public record CardListResponse(
            List<CardView> cards,
            /** 할부 잔여 원금 합계. 청구 여부와 무관하게 이미 갚기로 한 돈이다. */
            long installmentOutstanding
    ) {
    }
}
