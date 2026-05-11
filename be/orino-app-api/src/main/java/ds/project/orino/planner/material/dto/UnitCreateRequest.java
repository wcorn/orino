package ds.project.orino.planner.material.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UnitCreateRequest(
        @NotEmpty(message = "단위를 1개 이상 입력해주세요.")
        @Valid
        List<Item> units
) {
    public record Item(
            @NotBlank(message = "단위 제목을 입력해주세요.")
            @Size(min = 1, max = 200, message = "제목은 1~200자여야 합니다.")
            String title
    ) {
    }
}
