package ds.project.orino.planner.google.task.dto;

/**
 * 할 일 수정/완료 토글 요청. 모든 필드 선택(부분 갱신). completed로 완료/미완료 토글.
 */
public record TaskUpdateRequest(
        String title,
        String due,
        String notes,
        Boolean completed
) {
}
