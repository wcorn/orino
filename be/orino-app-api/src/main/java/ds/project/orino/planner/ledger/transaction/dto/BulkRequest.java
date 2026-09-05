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
        Long categoryId,
        /**
         * {@link Action#ATTACH_TRIP}이 붙일 여행. <b>{@code null}이면 연결을 끊는다</b> —
         * {@code SET_CATEGORY}에 {@code categoryId}를 비워 보내면 미분류가 되는 것과 같은 규칙이라
         * 「해제」를 위한 동작을 따로 만들지 않는다.
         */
        Long tripId
) {

    public enum Action {
        SET_CATEGORY,
        /** 소프트 삭제다. 행은 남는다. */
        DELETE,
        /**
         * 고른 거래를 여행에 붙이거나 뗀다(여행 v2.2 §18).
         *
         * <p>경비 화면이 다 밀려도 이것 하나면 「다녀와서 얼마 들었나」는 답이 나온다 —
         * 여행 중엔 가계부에 그냥 적고, 돌아와 기간으로 걸러 한 번 붙이면 된다.
         */
        ATTACH_TRIP
    }
}
