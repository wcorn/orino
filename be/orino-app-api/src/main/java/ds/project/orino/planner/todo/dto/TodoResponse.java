package ds.project.orino.planner.todo.dto;

import ds.project.orino.domain.todo.entity.Priority;
import ds.project.orino.domain.todo.entity.Todo;
import ds.project.orino.domain.todo.entity.TodoStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TodoResponse(
        Long id,
        String title,
        String description,
        Long categoryId,
        Long goalId,
        Priority priority,
        LocalDate deadline,
        Integer estimatedMinutes,
        TodoStatus status,
        LocalDateTime completedAt
) {

    public static TodoResponse from(Todo todo) {
        return new TodoResponse(
                todo.getId(),
                todo.getTitle(),
                todo.getDescription(),
                todo.getCategory() != null ? todo.getCategory().getId() : null,
                todo.getGoal() != null ? todo.getGoal().getId() : null,
                todo.getPriority(),
                todo.getDeadline(),
                todo.getEstimatedMinutes(),
                todo.getStatus(),
                todo.getCompletedAt()
        );
    }
}
