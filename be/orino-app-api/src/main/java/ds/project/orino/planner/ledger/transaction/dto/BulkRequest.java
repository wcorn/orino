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
     * 여행에 붙이는 동작은 <b>여기 없다.</b> 여행이 자기 장부를 갖게 되면서(#1406)
     * 붙일 원장이 없어졌고, 여행 지출은 {@code /api/travel/trips/{id}/expenses}에서 직접
     * 만든다. 이 원장에 남은 {@code tripId}는 이관이 끝날 때까지의 원본일 뿐이다.
     */
    public enum Action {
        SET_CATEGORY,
        /** 소프트 삭제다. 행은 남는다. */
        DELETE
    }
}
