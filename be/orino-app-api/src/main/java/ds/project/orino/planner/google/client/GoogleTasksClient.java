package ds.project.orino.planner.google.client;

import ds.project.orino.planner.google.calendar.dto.PlannerTask;
import ds.project.orino.planner.google.config.GoogleOAuthProperties;
import ds.project.orino.planner.google.task.dto.GoogleTaskWriteBody;
import ds.project.orino.planner.google.task.dto.GoogleTasksResponse;
import ds.project.orino.planner.google.task.dto.GoogleTasksResponse.GoogleTaskItem;
import ds.project.orino.planner.google.task.dto.TaskCreateRequest;
import ds.project.orino.planner.google.task.dto.TaskUpdateRequest;
import ds.project.orino.planner.google.token.GoogleTokenProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Google Tasks API 래퍼. 기본 task list("@default")에 대해 list/insert/patch/delete를 프록시한다.
 *
 * <p>Tasks는 Calendar와 호스트가 다르다(tasks.googleapis.com). access token은
 * {@link GoogleTokenProvider#executeWithRetry}로 공급·401 재시도한다. 미연동이면 GOOGLE_NOT_CONNECTED(409).
 */
@Component
public class GoogleTasksClient {

    private static final String TASKS_PATH = "/tasks/v1/lists/@default/tasks";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_NEEDS_ACTION = "needsAction";

    private final GoogleTokenProvider tokenProvider;
    private final RestClient googleRestClient;
    private final GoogleOAuthProperties oauthProperties;

    public GoogleTasksClient(GoogleTokenProvider tokenProvider,
                             RestClient googleRestClient,
                             GoogleOAuthProperties oauthProperties) {
        this.tokenProvider = tokenProvider;
        this.googleRestClient = googleRestClient;
        this.oauthProperties = oauthProperties;
    }

    public List<PlannerTask> listTasks(Long memberId, boolean showCompleted) {
        URI uri = UriComponentsBuilder.fromUriString(oauthProperties.tasksApiBaseUrl())
                .path(TASKS_PATH)
                .queryParam("showCompleted", showCompleted)
                .queryParam("showHidden", showCompleted)
                .queryParam("maxResults", "100")
                .build()
                .toUri();

        GoogleTasksResponse response = tokenProvider.executeWithRetry(memberId, accessToken ->
                googleRestClient.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .retrieve()
                        .body(GoogleTasksResponse.class));

        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items().stream().map(GoogleTasksClient::normalize).toList();
    }

    public PlannerTask insertTask(Long memberId, TaskCreateRequest request) {
        GoogleTaskWriteBody body = new GoogleTaskWriteBody(
                request.title(), request.notes(), toGoogleDue(request.due()), null);
        GoogleTaskItem item = tokenProvider.executeWithRetry(memberId, accessToken ->
                googleRestClient.post()
                        .uri(tasksUri())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(GoogleTaskItem.class));
        return normalize(item);
    }

    public PlannerTask patchTask(Long memberId, String taskId, TaskUpdateRequest request) {
        String status = request.completed() == null
                ? null
                : (request.completed() ? STATUS_COMPLETED : STATUS_NEEDS_ACTION);
        GoogleTaskWriteBody body = new GoogleTaskWriteBody(
                request.title(), request.notes(), toGoogleDue(request.due()), status);

        GoogleTaskItem item = tokenProvider.executeWithRetry(memberId, accessToken ->
                googleRestClient.patch()
                        .uri(taskUri(taskId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(GoogleTaskItem.class));
        return normalize(item);
    }

    public void deleteTask(Long memberId, String taskId) {
        tokenProvider.executeWithRetry(memberId, accessToken -> {
            googleRestClient.delete()
                    .uri(taskUri(taskId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    private URI tasksUri() {
        return UriComponentsBuilder.fromUriString(oauthProperties.tasksApiBaseUrl())
                .path(TASKS_PATH)
                .build()
                .toUri();
    }

    private URI taskUri(String taskId) {
        return UriComponentsBuilder.fromUriString(oauthProperties.tasksApiBaseUrl())
                .path(TASKS_PATH)
                .pathSegment(taskId)
                .build()
                .toUri();
    }

    private static PlannerTask normalize(GoogleTaskItem item) {
        String due = (item.due() != null && item.due().length() >= 10)
                ? item.due().substring(0, 10)
                : null;
        return new PlannerTask(
                item.id(),
                item.title(),
                due,
                STATUS_COMPLETED.equals(item.status()),
                item.notes(),
                "google");
    }

    /** 날짜("2026-06-12") → Google due RFC3339(자정 UTC). null이면 null. */
    private static String toGoogleDue(String date) {
        return date != null ? date + "T00:00:00.000Z" : null;
    }
}
