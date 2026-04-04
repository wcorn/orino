package ds.project.orino.planner.todo.dto;

import ds.project.orino.domain.todo.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateTodoRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        Long categoryId,
        Long goalId,
        Priority priority,
        LocalDate deadline,
        Integer estimatedMinutes
) {
}
