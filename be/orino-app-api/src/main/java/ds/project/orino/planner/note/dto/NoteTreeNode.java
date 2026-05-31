package ds.project.orino.planner.note.dto;

import java.util.List;

public record NoteTreeNode(
        Long id,
        String title,
        Long parentId,
        int sortOrder,
        List<NoteTreeNode> children
) {
}
