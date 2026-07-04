package ds.project.orino.memo.dto;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.memo.entity.Memo;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

public record MemoDetailResponse(
        Long id,
        Long parentId,
        String title,
        int sortOrder,
        JsonNode content,
        Instant updatedAt
) {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public static MemoDetailResponse of(Memo memo) {
        JsonNode parsed;
        try {
            parsed = MAPPER.readTree(memo.getContent());
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
        return new MemoDetailResponse(
                memo.getId(), memo.getParentId(),
                memo.getTitle(), memo.getSortOrder(), parsed, memo.getUpdatedAt());
    }
}
