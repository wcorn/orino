package ds.project.orino.planner.ledger.transaction.dto;

import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransaction;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionSource;
import ds.project.orino.domain.planner.ledger.entity.LedgerTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 거래 한 줄.
 *
 * @param amount 원화 환산액. <b>집계는 전부 이 값만 읽는다</b> — {@code fx}는 표시용 근거다
 * @param fx     외화 근거. 원화 거래면 {@code null}
 */
public record TransactionView(
        Long id,
        LedgerFlow type,
        LedgerTransactionStatus status,
        LocalDate occurredOn,
        LocalDateTime occurredAt,
        long amount,
        Long assetId,
        String assetName,
        Long counterAssetId,
        String counterAssetName,
        Long categoryId,
        String categoryName,
        String title,
        String memo,
        LedgerTransactionSource source,
        boolean estimated,
        Long refundOfId,
        List<String> tags,
        FxView fx
) {

    public static TransactionView of(LedgerTransaction tx,
                                     String assetName,
                                     String counterAssetName,
                                     String categoryName,
                                     List<String> tags) {
        return new TransactionView(
                tx.getId(), tx.getType(), tx.getStatus(), tx.getOccurredOn(), tx.getOccurredAt(),
                tx.getAmount(), tx.getAssetId(), assetName,
                tx.getCounterAssetId(), counterAssetName,
                tx.getCategoryId(), categoryName,
                tx.getTitle(), tx.getMemo(), tx.getSource(), tx.isEstimated(),
                tx.getRefundOfId(), tags,
                tx.hasFx()
                        ? new FxView(tx.getFxCurrency(), tx.getFxAmount(), tx.getFxRate())
                        : null);
    }

    /** 표시용 외화 근거. 통계·예산·잔액은 이 값을 읽지 않는다. */
    public record FxView(String currency, BigDecimal amount, BigDecimal rate) {
    }
}
