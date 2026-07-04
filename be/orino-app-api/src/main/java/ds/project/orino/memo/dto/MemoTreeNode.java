package ds.project.orino.memo.dto;

import java.util.List;

public record MemoTreeNode(
        Long id,
        String title,
        Long parentId,
        int sortOrder,
        List<MemoTreeNode> children
) {
}
