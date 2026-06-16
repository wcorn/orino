package ds.project.orino.planner.google.task.dto;

import jakarta.validation.constraints.NotBlank;

/** 할 일 생성 요청. due는 마감 날짜("2026-06-12") 또는 null. */
public record TaskCreateRequest(
        @NotBlank String title,
        String due,
        String notes
) {
}
