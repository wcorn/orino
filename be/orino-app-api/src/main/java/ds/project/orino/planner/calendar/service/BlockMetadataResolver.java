package ds.project.orino.planner.calendar.service;

import ds.project.orino.domain.calendar.entity.BlockType;
import ds.project.orino.domain.calendar.entity.ScheduleBlock;
import ds.project.orino.domain.category.entity.Category;
import ds.project.orino.domain.fixedschedule.entity.FixedSchedule;
import ds.project.orino.domain.fixedschedule.repository.FixedScheduleRepository;
import ds.project.orino.domain.material.entity.StudyUnit;
import ds.project.orino.domain.material.repository.StudyUnitRepository;
import ds.project.orino.domain.review.entity.ReviewSchedule;
import ds.project.orino.domain.review.repository.ReviewScheduleRepository;
import ds.project.orino.domain.routine.entity.Routine;
import ds.project.orino.domain.routine.repository.RoutineRepository;
import ds.project.orino.domain.todo.entity.Todo;
import ds.project.orino.domain.todo.repository.TodoRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * blockType + referenceId 조합으로 표시용 메타정보(title, category)를 조회한다.
 * 블록 단위로 N+1 쿼리가 발생하지 않도록 type별로 묶어 in-query로 한번에 가져온다.
 */
@Component
public class BlockMetadataResolver {

    private final TodoRepository todoRepository;
    private final FixedScheduleRepository fixedScheduleRepository;
    private final RoutineRepository routineRepository;
    private final StudyUnitRepository studyUnitRepository;
    private final ReviewScheduleRepository reviewScheduleRepository;

    public BlockMetadataResolver(
            TodoRepository todoRepository,
            FixedScheduleRepository fixedScheduleRepository,
            RoutineRepository routineRepository,
            StudyUnitRepository studyUnitRepository,
            ReviewScheduleRepository reviewScheduleRepository) {
        this.todoRepository = todoRepository;
        this.fixedScheduleRepository = fixedScheduleRepository;
        this.routineRepository = routineRepository;
        this.studyUnitRepository = studyUnitRepository;
        this.reviewScheduleRepository = reviewScheduleRepository;
    }

    public Map<Long, BlockMetadata> resolve(List<ScheduleBlock> blocks) {
        Map<BlockType, List<Long>> byType = groupReferenceIdsByType(blocks);
        Map<BlockType, Map<Long, BlockMetadata>> metaByType =
                new EnumMap<>(BlockType.class);
        for (Map.Entry<BlockType, List<Long>> entry : byType.entrySet()) {
            metaByType.put(entry.getKey(),
                    resolveByType(entry.getKey(), entry.getValue()));
        }

        Map<Long, BlockMetadata> result = new HashMap<>();
        for (ScheduleBlock block : blocks) {
            BlockMetadata meta = metaByType
                    .getOrDefault(block.getBlockType(), Map.of())
                    .get(block.getReferenceId());
            result.put(block.getId(),
                    meta != null ? meta : BlockMetadata.unknown());
        }
        return result;
    }

    private Map<BlockType, List<Long>> groupReferenceIdsByType(
            List<ScheduleBlock> blocks) {
        Map<BlockType, List<Long>> result = new EnumMap<>(BlockType.class);
        for (ScheduleBlock block : blocks) {
            result.computeIfAbsent(block.getBlockType(),
                    k -> new ArrayList<>()).add(block.getReferenceId());
        }
        return result;
    }

    private Map<Long, BlockMetadata> resolveByType(BlockType type,
                                                   List<Long> ids) {
        return switch (type) {
            case FIXED -> resolveFixed(ids);
            case ROUTINE -> resolveRoutine(ids);
            case TODO -> resolveTodo(ids);
            case STUDY -> resolveStudy(ids);
            case REVIEW -> resolveReview(ids);
        };
    }

    private Map<Long, BlockMetadata> resolveFixed(List<Long> ids) {
        Map<Long, BlockMetadata> result = new HashMap<>();
        for (FixedSchedule f : fixedScheduleRepository.findAllById(ids)) {
            result.put(f.getId(), new BlockMetadata(
                    f.getTitle(),
                    categoryName(f.getCategory()),
                    categoryColor(f.getCategory())));
        }
        return result;
    }

    private Map<Long, BlockMetadata> resolveRoutine(List<Long> ids) {
        Map<Long, BlockMetadata> result = new HashMap<>();
        for (Routine r : routineRepository.findAllById(ids)) {
            result.put(r.getId(), new BlockMetadata(
                    r.getTitle(),
                    categoryName(r.getCategory()),
                    categoryColor(r.getCategory())));
        }
        return result;
    }

    private Map<Long, BlockMetadata> resolveTodo(List<Long> ids) {
        Map<Long, BlockMetadata> result = new HashMap<>();
        for (Todo t : todoRepository.findAllById(ids)) {
            result.put(t.getId(), new BlockMetadata(
                    t.getTitle(),
                    categoryName(t.getCategory()),
                    categoryColor(t.getCategory())));
        }
        return result;
    }

    private Map<Long, BlockMetadata> resolveStudy(List<Long> ids) {
        Map<Long, BlockMetadata> result = new HashMap<>();
        for (StudyUnit u : studyUnitRepository.findAllById(ids)) {
            Category category = u.getMaterial().getCategory();
            String title = u.getMaterial().getTitle() + " - " + u.getTitle();
            result.put(u.getId(), new BlockMetadata(
                    title,
                    categoryName(category),
                    categoryColor(category)));
        }
        return result;
    }

    private Map<Long, BlockMetadata> resolveReview(List<Long> ids) {
        Map<Long, BlockMetadata> result = new HashMap<>();
        for (ReviewSchedule r : reviewScheduleRepository.findAllById(ids)) {
            StudyUnit unit = r.getStudyUnit();
            Category category = unit.getMaterial().getCategory();
            String title = String.format("[복습 %d회] %s - %s",
                    r.getSequence(),
                    unit.getMaterial().getTitle(), unit.getTitle());
            result.put(r.getId(), new BlockMetadata(
                    title,
                    categoryName(category),
                    categoryColor(category)));
        }
        return result;
    }

    private String categoryName(Category category) {
        return category != null ? category.getName() : null;
    }

    private String categoryColor(Category category) {
        return category != null ? category.getColor() : "#888888";
    }
}
