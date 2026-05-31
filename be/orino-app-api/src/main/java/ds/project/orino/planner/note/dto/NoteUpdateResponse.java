package ds.project.orino.planner.note.dto;

import ds.project.orino.domain.planner.note.entity.Note;

import java.time.LocalDateTime;

public record NoteUpdateResponse(
        Long id,
        Long materialId,
        Long parentId,
        String title,
        int sortOrder,
        LocalDateTime updatedAt
) {
    public static NoteUpdateResponse of(Note note) {
        return new NoteUpdateResponse(
                note.getId(), note.getMaterialId(), note.getParentId(),
                note.getTitle(), note.getSortOrder(), note.getUpdatedAt());
    }
}
