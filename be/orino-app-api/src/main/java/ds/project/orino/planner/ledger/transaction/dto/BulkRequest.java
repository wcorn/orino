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

    public enum Action {
        SET_CATEGORY,
        /** 소프트 삭제다. 행은 남는다. */
        DELETE
    }
}
