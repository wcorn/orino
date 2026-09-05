package ds.project.orino.planner.ledger.transaction.dto;

import ds.project.orino.domain.planner.ledger.entity.LedgerFlow;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 거래 입력.
 *
 * <p>필수는 <b>금액 · 날짜 · 자산 · 유형</b>이다. 카테고리는 빠져도 된다 — 미분류를 허용한다.
 * 기록을 막느니 나중에 채운다(확정 명세 §4.2).
 *
 * @param amount     원 단위, 양수. 외화 거래면 생략하고 {@code fx}를 보낸다 —
 *                   그때는 서버가 {@code round(fx.amount × rate)}로 확정한다
 * @param occurredOn <b>미래면 예정으로 저장된다.</b> 별도 메뉴를 외우게 하지 않는다
 */
public record TransactionCreateRequest(
        @NotNull LedgerFlow type,
        @Positive Long amount,
        @NotNull LocalDate occurredOn,
        LocalDateTime occurredAt,
        // @NotNull을 걸지 않는다. 빠졌을 때 「잘못된 요청」이 아니라 「자산 없이는 거래를
        // 만들 수 없다」(LDG-ERR-002)로 답해야 한다 — 이 모듈에서 그건 형식 오류가 아니라
        // 지켜야 할 규칙이고, 화면도 그 문구를 그대로 보여준다.
        Long assetId,
        Long counterAssetId,
        Long categoryId,
        @Size(max = 120) String title,
        @Size(max = 500) String memo,
        List<String> tags,
        Boolean estimated,
        FxInput fx,
        /** 신용카드 할부. 원 거래에는 <b>전액</b>이 적히고 회차는 따로 만들어진다. */
        InstallmentInput installment,
        /**
         * 어느 여행의 지출인지. 없는 여행·남의 여행이면 404다 — {@code @NotNull}을 걸지 않는
         * 이유는 {@code assetId}와 같다. 대부분의 거래는 여행과 무관하다.
         */
        Long tripId
) {

    /**
     * @param months       2~60. 카드사가 파는 범위 밖은 입력 실수로 본다
     * @param interestFree 무이자 여부. <b>부채 계산에는 영향이 없다</b> —
     *                     갚기로 한 원금은 무이자든 아니든 이미 빚이다
     */
    public record InstallmentInput(int months, Boolean interestFree) {
    }
}
