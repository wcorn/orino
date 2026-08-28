package ds.project.orino.planner.ledger.transaction.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 다건 입력(`LDG-015`).
 *
 * <p>한 줄이라도 거부되면 <b>전부 롤백</b>이다. 카드 명세서를 보며 몰아 적는 화면이라,
 * 일부만 들어가면 어디까지 옮겼는지 사람이 다시 맞춰야 한다.
 *
 * @param transactions 최대 100줄. 그보다 많으면 화면이 아니라 가져오기(#1268)의 일이다
 */
public record BulkCreateRequest(
        @NotEmpty @Size(max = 100) @Valid List<TransactionCreateRequest> transactions
) {
}
