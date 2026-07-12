package ds.project.orino.planner.dataset.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateRowRequest(
        @NotNull(message = "cells는 필수입니다.")
        List<String> cells
) {
}
