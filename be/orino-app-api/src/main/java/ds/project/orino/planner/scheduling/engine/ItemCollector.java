package ds.project.orino.planner.scheduling.engine;

import ds.project.orino.domain.calendar.entity.BlockType;
import ds.project.orino.domain.material.entity.DeadlineMode;
import ds.project.orino.domain.material.entity.MaterialStatus;
import ds.project.orino.domain.material.entity.StudyMaterial;
import ds.project.orino.domain.material.entity.StudyUnit;
import ds.project.orino.domain.material.entity.UnitStatus;
import ds.project.orino.domain.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.review.entity.ReviewSchedule;
import ds.project.orino.domain.review.entity.ReviewStatus;
import ds.project.orino.domain.review.repository.ReviewScheduleRepository;
import ds.project.orino.domain.todo.entity.Todo;
import ds.project.orino.domain.todo.entity.TodoStatus;
import ds.project.orino.domain.todo.repository.TodoRepository;
import ds.project.orino.domain.todo.repository.TodoSpecification;
import ds.project.orino.planner.scheduling.engine.model.ItemCategory;
import ds.project.orino.planner.scheduling.engine.model.SchedulableItem;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 주어진 날짜에 스케줄링할 항목을 수집한다.
 * 밀린 복습 / 오늘 복습 / 마감 임박 할 일 / 학습 단위 / 기한 없는 할 일.
 */
@Component
public class ItemCollector {

    /** 할 일 마감 임박 기준일수. */
    private static final int URGENT_TODO_DAYS = 3;
    /** 학습 단위 기본 소요시간 (없을 때 사용). */
    private static final int DEFAULT_UNIT_MINUTES = 30;

    private final ReviewScheduleRepository reviewRepository;
    private final TodoRepository todoRepository;
    private final StudyMaterialRepository materialRepository;

    public ItemCollector(ReviewScheduleRepository reviewRepository,
                         TodoRepository todoRepository,
                         StudyMaterialRepository materialRepository) {
        this.reviewRepository = reviewRepository;
        this.todoRepository = todoRepository;
        this.materialRepository = materialRepository;
    }

    public List<SchedulableItem> collect(Long memberId, LocalDate date) {
        List<SchedulableItem> items = new ArrayList<>();
        items.addAll(collectReviews(memberId, date));
        items.addAll(collectTodos(memberId, date));
        items.addAll(collectStudyUnits(memberId));
        return items;
    }

    private List<SchedulableItem> collectReviews(Long memberId,
                                                 LocalDate date) {
        List<ReviewSchedule> due = reviewRepository.findDueByMember(
                memberId, date,
                List.of(ReviewStatus.PENDING, ReviewStatus.OVERDUE));

        List<SchedulableItem> result = new ArrayList<>();
        for (ReviewSchedule r : due) {
            boolean overdue = r.getScheduledDate().isBefore(date)
                    || r.getStatus() == ReviewStatus.OVERDUE;
            ItemCategory category = overdue
                    ? ItemCategory.OVERDUE_REVIEW
                    : ItemCategory.TODAY_REVIEW;
            StudyUnit unit = r.getStudyUnit();
            int minutes = Math.max(1, unit.getEstimatedMinutes() / 2);
            int subOrder = toDayOrdinal(r.getScheduledDate());
            result.add(SchedulableItem.builder()
                    .category(category)
                    .blockType(BlockType.REVIEW)
                    .referenceId(r.getId())
                    .estimatedMinutes(minutes)
                    .due(r.getScheduledDate())
                    .subOrder(subOrder)
                    .materialId(unit.getMaterial().getId())
                    .title(unit.getTitle() + " 복습 " + r.getSequence() + "회차")
                    .build());
        }
        return result;
    }

    private List<SchedulableItem> collectTodos(Long memberId,
                                               LocalDate date) {
        Specification<Todo> spec = Specification
                .where(TodoSpecification.memberIdEquals(memberId))
                .and(TodoSpecification.statusEquals(TodoStatus.PENDING));
        List<Todo> todos = todoRepository.findAll(spec);

        LocalDate urgentBy = date.plusDays(URGENT_TODO_DAYS);
        List<SchedulableItem> result = new ArrayList<>();
        for (Todo todo : todos) {
            int minutes = todo.getEstimatedMinutes() != null
                    ? todo.getEstimatedMinutes() : DEFAULT_UNIT_MINUTES;
            LocalDate deadline = todo.getDeadline();
            ItemCategory category;
            int subOrder;
            if (deadline != null && !deadline.isAfter(urgentBy)) {
                category = ItemCategory.URGENT_TODO;
                subOrder = toDayOrdinal(deadline);
            } else {
                category = ItemCategory.NO_DEADLINE_TODO;
                subOrder = deadline != null
                        ? toDayOrdinal(deadline) : Integer.MAX_VALUE;
            }
            result.add(SchedulableItem.builder()
                    .category(category)
                    .blockType(BlockType.TODO)
                    .referenceId(todo.getId())
                    .estimatedMinutes(minutes)
                    .due(deadline)
                    .subOrder(subOrder)
                    .title(todo.getTitle())
                    .build());
        }
        return result;
    }

    private List<SchedulableItem> collectStudyUnits(Long memberId) {
        List<StudyMaterial> materials = materialRepository
                .findByMemberIdOrderByCreatedAtDesc(memberId);
        List<SchedulableItem> result = new ArrayList<>();
        for (StudyMaterial material : materials) {
            if (material.getStatus() != MaterialStatus.ACTIVE) {
                continue;
            }
            List<StudyUnit> pending = material.getUnits().stream()
                    .filter(u -> u.getStatus() == UnitStatus.PENDING)
                    .sorted(Comparator.comparingInt(StudyUnit::getSortOrder))
                    .toList();
            boolean hasDeadline =
                    material.getDeadlineMode() == DeadlineMode.DEADLINE
                            && material.getDeadline() != null;
            ItemCategory category = hasDeadline
                    ? ItemCategory.DEADLINE_STUDY
                    : ItemCategory.NEW_STUDY;
            for (StudyUnit unit : pending) {
                int subOrder = hasDeadline
                        ? toDayOrdinal(material.getDeadline()) * 10000
                        + unit.getSortOrder()
                        : unit.getSortOrder();
                result.add(SchedulableItem.builder()
                        .category(category)
                        .blockType(BlockType.STUDY)
                        .referenceId(unit.getId())
                        .estimatedMinutes(unit.getEstimatedMinutes())
                        .due(material.getDeadline())
                        .subOrder(subOrder)
                        .materialId(material.getId())
                        .title(material.getTitle() + " - " + unit.getTitle())
                        .build());
            }
        }
        return result;
    }

    private int toDayOrdinal(LocalDate date) {
        if (date == null) {
            return Integer.MAX_VALUE;
        }
        long epoch = date.toEpochDay();
        if (epoch > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (epoch < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) epoch;
    }
}
