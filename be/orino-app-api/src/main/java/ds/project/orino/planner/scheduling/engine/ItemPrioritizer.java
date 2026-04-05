package ds.project.orino.planner.scheduling.engine;

import ds.project.orino.planner.scheduling.engine.model.SchedulableItem;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 수집된 항목을 우선순위에 따라 정렬한다.
 *
 * 현재 버전은 ItemCategory의 ordinal 순서(기본 우선순위)를 사용한다.
 * 사용자별 PriorityRule과의 연동은 추후 #143의 enum 정비 후 구현한다.
 */
@Component
public class ItemPrioritizer {

    public List<SchedulableItem> prioritize(List<SchedulableItem> items) {
        return items.stream()
                .sorted(Comparator
                        .comparing((SchedulableItem i) -> i.category().ordinal())
                        .thenComparingInt(SchedulableItem::subOrder)
                        .thenComparingLong(SchedulableItem::referenceId))
                .toList();
    }
}
