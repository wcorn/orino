package ds.project.orino.planner.google.routine.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 습관 완료 체크 토글 요청.
 *
 * @param date 완료 인스턴스의 사용자 시간대 로컬 날짜("2026-06-20")
 * @param done true=체크(upsert) / false=해제(delete)
 */
public record RoutineCheckRequest(
        @NotNull LocalDate date,
        boolean done
) {
}
