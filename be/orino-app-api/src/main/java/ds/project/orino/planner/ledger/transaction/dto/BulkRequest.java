package ds.project.orino.planner.ledger.transaction.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 일괄 편집·삭제. 미분류 정리(#1261)가 이 API로 수십 건을 한 번에 넘긴다.
 *
 * @param categoryId {@link Action#SET_CATEGORY}일 때 붙일 카테고리.
 *                   {@code null}이면 미분류로 되돌린다
 */
public record BulkRequest(
        @NotNull Action action,
        @NotEmpty List<Long> ids,
        Long categoryId
) {

    /**
     * 여행에 붙이는 동작은 <b>여기 없다.</b> 그건 「어느 여행의 지출인가」를 정하는 일이라
     * 여행이 아는 것이고, 가계부에 두면 가계부가 여행의 존재와 소유권을 알아야 한다 —
     * 의존이 양방향이 되는 자리다. {@code POST /api/travel/trips/{id}/expenses/attach}에 있다.
     */
    public enum Action {
        SET_CATEGORY,
        /** 소프트 삭제다. 행은 남는다. */
        DELETE
    }
}
