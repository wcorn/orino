package ds.project.orino.planner.note.dto;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.note.entity.Note;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

public record NoteDetailResponse(
        Long id,
        Long materialId,
        Long parentId,
        String title,
        int sortOrder,
        JsonNode content,
        LocalDateTime updatedAt
) {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public static NoteDetailResponse of(Note note) {
        JsonNode parsed;
        try {
            parsed = MAPPER.readTree(note.getContent());
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
        return new NoteDetailResponse(
                note.getId(), note.getMaterialId(), note.getParentId(),
                note.getTitle(), note.getSortOrder(), parsed, note.getUpdatedAt());
    }
}
