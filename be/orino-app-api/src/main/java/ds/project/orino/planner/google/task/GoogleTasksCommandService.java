package ds.project.orino.planner.google.task;

import ds.project.orino.planner.google.calendar.dto.PlannerTask;
import ds.project.orino.planner.google.client.GoogleTasksClient;
import ds.project.orino.planner.google.task.dto.TaskCreateRequest;
import ds.project.orino.planner.google.task.dto.TaskUpdateRequest;
import ds.project.orino.redis.planner.google.GoogleTasksCacheRepository;
import org.springframework.stereotype.Service;

/**
 * 할 일 쓰기(생성/수정/삭제) 오케스트레이션. Google 프록시 후 해당 사용자 단기 캐시를 무효화한다.
 *
 * <p>미연동이면 {@link GoogleTasksClient}의 토큰 공급 단계에서 GOOGLE_NOT_CONNECTED(409)가 발생한다.
 */
@Service
public class GoogleTasksCommandService {

    private final GoogleTasksClient tasksClient;
    private final GoogleTasksCacheRepository cacheRepository;

    public GoogleTasksCommandService(GoogleTasksClient tasksClient,
                                     GoogleTasksCacheRepository cacheRepository) {
        this.tasksClient = tasksClient;
        this.cacheRepository = cacheRepository;
    }

    public PlannerTask create(Long memberId, TaskCreateRequest request) {
        PlannerTask created = tasksClient.insertTask(memberId, request);
        cacheRepository.evictAll(memberId);
        return created;
    }

    public PlannerTask update(Long memberId, String taskId, TaskUpdateRequest request) {
        PlannerTask updated = tasksClient.patchTask(memberId, taskId, request);
        cacheRepository.evictAll(memberId);
        return updated;
    }

    public void delete(Long memberId, String taskId) {
        tasksClient.deleteTask(memberId, taskId);
        cacheRepository.evictAll(memberId);
    }
}
