package ds.project.orino.planner.material.dto;

import ds.project.orino.planner.note.dto.NoteResponse;

public record MaterialCreateResponse(MaterialResponse material, NoteResponse note) {
}
