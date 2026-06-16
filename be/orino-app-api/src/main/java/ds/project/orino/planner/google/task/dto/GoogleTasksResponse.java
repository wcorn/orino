package ds.project.orino.planner.google.task.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Google Tasks tasks.list 응답(필요한 필드만). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleTasksResponse(List<GoogleTaskItem> items) {

    /** status: "needsAction" | "completed". due: RFC3339(날짜만 의미). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoogleTaskItem(
            String id,
            String title,
            String notes,
            String status,
            String due
    ) {
    }
}
