package ds.project.orino.planner.ledger.transaction.dto;

import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 거래 수정. <b>보낸 것만 바꾼다</b> — 빠진 필드는 건드리지 않는다.
 *
 * <p>{@code categoryId}를 지우려면(미분류로 되돌리려면) {@code clearCategory}를 쓴다.
 * {@code null}은 「안 보냈다」와 「비우겠다」를 구분하지 못하고, 미분류로 되돌리는 것은
 * 이 모듈에서 실제로 일어나는 조작이다.
 */
public record TransactionUpdateRequest(
        LedgerFlow type,
        @Positive Long amount,
        LocalDate occurredOn,
        LocalDateTime occurredAt,
        Long assetId,
        Long counterAssetId,
        Long categoryId,
        Boolean clearCategory,
        @Size(max = 120) String title,
        @Size(max = 500) String memo,
        List<String> tags,
        Boolean estimated,
        FxInput fx,
        /** 외화 근거를 지우고 원화 거래로 되돌린다. {@code amount}를 함께 보내야 한다. */
        Boolean clearFx
) {
}
