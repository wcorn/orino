package ds.project.orino.planner.reflection.dto;

import ds.project.orino.domain.reflection.entity.DailyReflection;

import java.time.LocalDate;

public record ReflectionResponse(
        Long id,
        LocalDate date,
        int mood,
        String memo) {

    public static ReflectionResponse from(DailyReflection entity) {
        return new ReflectionResponse(
                entity.getId(),
                entity.getReflectionDate(),
                entity.getMood(),
                entity.getMemo());
    }
}
