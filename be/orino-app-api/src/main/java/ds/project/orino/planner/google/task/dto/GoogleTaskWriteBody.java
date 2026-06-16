package ds.project.orino.planner.google.task.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Google Tasks tasks.insert/patch 요청 바디. null 필드는 직렬화 제외(patch 부분 갱신). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoogleTaskWriteBody(
        String title,
        String notes,
        String due,
        String status
) {
}
