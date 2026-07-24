package ds.project.orino.planner.lifelog.flow.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 흐름 내 순서 조정. 이 목록 순서대로 sort_order를 재기록한다(목록에 없는 소속은 뒤로 밀린다).
 */
public record ReorderMomentsRequest(
        @NotNull
        List<Long> momentIds
) {
}
