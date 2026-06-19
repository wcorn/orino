package ds.project.orino.planner.google.task;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.planner.google.calendar.dto.PlannerTask;
import ds.project.orino.planner.google.client.GoogleTasksClient;
import ds.project.orino.redis.planner.google.GoogleTasksCacheRepository;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 할 일 조회: 단기 캐시(~60초) + 라이브 프록시. 통합 피드(#479)와 단독 목록 조회가 이 결과를 사용한다.
 *
 * <p>매 요청 Tasks API를 라이브 호출하던 지연(#544)을 일정과 동일한 캐시 패턴으로 흡수한다.
 * 미연동/연동만료는 {@link GoogleTasksClient}가 예외로 던지므로 캐시하지 않고 그대로 전파한다
 * (단독 엔드포인트의 409 시맨틱 보존, 피드에서는 호출부가 빈 목록으로 처리).
 */
@Service
public class GoogleTasksQueryService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final TypeReference<List<PlannerTask>> TASK_LIST = new TypeReference<>() {
    };

    private final GoogleTasksClient tasksClient;
    private final GoogleTasksCacheRepository cacheRepository;
    private final ObjectMapper objectMapper;

    public GoogleTasksQueryService(GoogleTasksClient tasksClient,
                                   GoogleTasksCacheRepository cacheRepository,
                                   ObjectMapper objectMapper) {
        this.tasksClient = tasksClient;
        this.cacheRepository = cacheRepository;
        this.objectMapper = objectMapper;
    }

    public List<PlannerTask> listTasks(Long memberId, boolean showCompleted) {
        Optional<String> cached = cacheRepository.find(memberId, showCompleted);
        if (cached.isPresent()) {
            return deserialize(cached.get());
        }

        List<PlannerTask> tasks = tasksClient.listTasks(memberId, showCompleted);
        cacheRepository.save(memberId, showCompleted, serialize(tasks), CACHE_TTL);
        return tasks;
    }

    private String serialize(List<PlannerTask> tasks) {
        try {
            return objectMapper.writeValueAsString(tasks);
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.GOOGLE_API_FAILED, e);
        }
    }

    private List<PlannerTask> deserialize(String json) {
        try {
            return objectMapper.readValue(json, TASK_LIST);
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.GOOGLE_API_FAILED, e);
        }
    }
}
