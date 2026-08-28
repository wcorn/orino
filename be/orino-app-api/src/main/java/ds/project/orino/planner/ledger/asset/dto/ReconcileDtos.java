package ds.project.orino.planner.ledger.asset.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 잔액 맞추기 DTO(`LDG-004`). */
public final class ReconcileDtos {

    private ReconcileDtos() {
    }

    /**
     * @param actualBalance 통장·앱에서 실제로 확인한 잔액
     * @param occurredOn    조정 거래의 날짜. 생략하면 오늘 — 어긋남을 <b>발견한 날</b>이다
     */
    public record Request(
            @NotNull Long actualBalance,
            LocalDate occurredOn,
            @Size(max = 500) String memo
    ) {
    }

    /**
     * @param difference 실제 − 원장. 양수면 원장이 덜 잡고 있었다는 뜻이다
     */
    public record Response(
            Long adjustmentTransactionId,
            long difference,
            long balanceAfter
    ) {
    }
}
