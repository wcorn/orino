package ds.project.orino.planner.category.dto;

import ds.project.orino.domain.category.entity.Category;

public record CategoryResponse(
        Long id,
        String name,
        String color,
        String icon,
        int sortOrder
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getColor(),
                category.getIcon(),
                category.getSortOrder()
        );
    }
}
