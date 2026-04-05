package ds.project.orino.planner.scheduling.engine;

import ds.project.orino.domain.calendar.entity.BlockType;
import ds.project.orino.planner.scheduling.engine.model.ItemCategory;
import ds.project.orino.planner.scheduling.engine.model.SchedulableItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItemPrioritizerTest {

    private final ItemPrioritizer prioritizer = new ItemPrioritizer();

    @Test
    @DisplayName("카테고리 우선순위 순서대로 정렬한다")
    void sortByCategory() {
        SchedulableItem noDeadline = itemOf(
                ItemCategory.NO_DEADLINE_TODO, 1, 1L);
        SchedulableItem overdue = itemOf(
                ItemCategory.OVERDUE_REVIEW, 1, 2L);
        SchedulableItem urgent = itemOf(
                ItemCategory.URGENT_TODO, 1, 3L);

        List<SchedulableItem> result = prioritizer.prioritize(
                List.of(noDeadline, overdue, urgent));

        assertThat(result).containsExactly(overdue, urgent, noDeadline);
    }

    @Test
    @DisplayName("같은 카테고리 내에서는 subOrder로 정렬한다")
    void sortBySubOrder() {
        SchedulableItem later = itemOf(
                ItemCategory.URGENT_TODO, 20, 1L);
        SchedulableItem earlier = itemOf(
                ItemCategory.URGENT_TODO, 10, 2L);

        List<SchedulableItem> result = prioritizer.prioritize(
                List.of(later, earlier));

        assertThat(result).containsExactly(earlier, later);
    }

    private SchedulableItem itemOf(ItemCategory category, int subOrder,
                                   long refId) {
        return SchedulableItem.builder()
                .category(category)
                .blockType(BlockType.TODO)
                .referenceId(refId)
                .estimatedMinutes(30)
                .subOrder(subOrder)
                .title("test" + refId)
                .build();
    }
}
