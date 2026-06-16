package ds.project.orino.planner.google.task.dto;

import ds.project.orino.planner.google.calendar.dto.PlannerTask;

import java.util.List;

/** 할 일 목록 응답. */
public record TasksResponse(List<PlannerTask> tasks) {
}
