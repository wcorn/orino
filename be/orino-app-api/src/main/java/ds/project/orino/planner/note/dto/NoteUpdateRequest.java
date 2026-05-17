package ds.project.orino.planner.note.dto;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record NoteUpdateRequest(
        @NotNull(message = "content는 필수입니다.")
        JsonNode content
) {
}
