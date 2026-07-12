package ds.project.orino.planner.dataset.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 행 추가. atIndex가 null이면 끝에 append, 있으면 그 위치에 삽입(뒤 행 시프트). */
public record InsertRowRequest(
        Integer atIndex,
        @NotNull(message = "cells는 필수입니다.")
        List<String> cells
) {
}
