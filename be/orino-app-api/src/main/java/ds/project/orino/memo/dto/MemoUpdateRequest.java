package ds.project.orino.memo.dto;

import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record MemoUpdateRequest(
        @Size(min = 1, max = 200, message = "title은 1~200자여야 합니다.")
        String title,

        JsonNode content,

        Long parentId,

        Integer sortOrder
) {
}
