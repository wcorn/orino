package ds.project.orino.planner.ledger.transaction.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 환불·취소.
 *
 * @param amount     부분 환불 금액. 생략하면 <b>남은 전액</b>이다. 이미 환불된 만큼을 빼고
 *                   계산하므로 같은 거래를 두 번 전액 환불할 수 없다
 * @param occurredOn 환불이 일어난 날. 생략하면 오늘 — 원 거래의 날짜가 아니다.
 *                   환불은 <b>나중에 일어난 별개의 사건</b>이다
 */
public record RefundRequest(
        @Positive Long amount,
        LocalDate occurredOn,
        @Size(max = 500) String memo
) {
}
