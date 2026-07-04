package ds.project.orino.memo.dto;

import ds.project.orino.domain.memo.entity.Memo;

import java.time.Instant;

public record MemoUpdateResponse(
        Long id,
        Long parentId,
        String title,
        int sortOrder,
        Instant updatedAt
) {
    public static MemoUpdateResponse of(Memo memo) {
        return new MemoUpdateResponse(
                memo.getId(), memo.getParentId(),
                memo.getTitle(), memo.getSortOrder(), memo.getUpdatedAt());
    }
}
