package ds.project.orino.planner.note.dto;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.note.entity.Note;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

public record NoteResponse(
        Long id,
        Long materialId,
        JsonNode content,
        LocalDateTime updatedAt
) {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public static NoteResponse of(Note note) {
        JsonNode parsed;
        try {
            parsed = MAPPER.readTree(note.getContent());
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
        return new NoteResponse(note.getId(), note.getMaterialId(), parsed, note.getUpdatedAt());
    }
}
