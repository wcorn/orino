package ds.project.orino.planner.lifelog.moment.dto;

/**
 * 기록이 담긴 흐름 요약(카드에서 "이 기록이 어느 흐름에" 표시용).
 */
public record FlowRef(
        Long id,
        String title
) {
}
