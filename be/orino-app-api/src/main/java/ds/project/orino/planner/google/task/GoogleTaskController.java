package ds.project.orino.planner.google.task;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.google.calendar.dto.PlannerTask;
import ds.project.orino.planner.google.client.GoogleTasksClient;
import ds.project.orino.planner.google.task.dto.TaskCreateRequest;
import ds.project.orino.planner.google.task.dto.TaskUpdateRequest;
import ds.project.orino.planner.google.task.dto.TasksResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Google Tasks 프록시(기본 task list). 미연동 시 409(PLN-ERR-003).
 */
@RestController
@RequestMapping("/api/planner/tasks")
public class GoogleTaskController {

    private final GoogleTasksClient googleTasksClient;

    public GoogleTaskController(GoogleTasksClient googleTasksClient) {
        this.googleTasksClient = googleTasksClient;
    }

    @GetMapping
    public ApiResponse<TasksResponse> list(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(defaultValue = "false") boolean showCompleted) {
        return ApiResponse.success(
                new TasksResponse(googleTasksClient.listTasks(memberId, showCompleted)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PlannerTask>> create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody TaskCreateRequest request) {
        PlannerTask created = googleTasksClient.insertTask(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PatchMapping("/{taskId}")
    public ApiResponse<PlannerTask> update(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String taskId,
            @RequestBody TaskUpdateRequest request) {
        return ApiResponse.success(googleTasksClient.patchTask(memberId, taskId, request));
    }

    @DeleteMapping("/{taskId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String taskId) {
        googleTasksClient.deleteTask(memberId, taskId);
        return ApiResponse.success();
    }
}
