package ds.project.orino.planner.calendar.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.calendar.entity.BlockStatus;
import ds.project.orino.domain.calendar.entity.DailySchedule;
import ds.project.orino.domain.calendar.entity.ScheduleBlock;
import ds.project.orino.domain.calendar.repository.DailyScheduleRepository;
import ds.project.orino.domain.calendar.repository.ScheduleBlockRepository;
import ds.project.orino.domain.material.entity.StudyUnit;
import ds.project.orino.domain.material.repository.StudyUnitRepository;
import ds.project.orino.domain.review.entity.ReviewSchedule;
import ds.project.orino.domain.review.repository.ReviewScheduleRepository;
import ds.project.orino.domain.routine.entity.Routine;
import ds.project.orino.domain.routine.entity.RoutineCheck;
import ds.project.orino.domain.routine.repository.RoutineCheckRepository;
import ds.project.orino.domain.routine.repository.RoutineRepository;
import ds.project.orino.domain.todo.entity.Todo;
import ds.project.orino.domain.todo.repository.TodoRepository;
import ds.project.orino.planner.calendar.dto.BlockEffect;
import ds.project.orino.planner.calendar.dto.CompleteBlockResponse;
import ds.project.orino.planner.calendar.dto.DailyProgress;
import ds.project.orino.planner.calendar.dto.DailyScheduleResponse;
import ds.project.orino.planner.calendar.dto.ReorderBlockRequest;
import ds.project.orino.planner.calendar.dto.ReorderBlockResponse;
import ds.project.orino.planner.calendar.dto.ScheduleBlockResponse;
import ds.project.orino.planner.calendar.dto.WarningResponse;
import ds.project.orino.planner.scheduling.engine.SchedulingEngine;
import ds.project.orino.planner.scheduling.engine.model.SchedulingResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 일간 캘린더 조회 및 블록 상태 변경 서비스.
 * 엔진 호출·메타정보 조회·블록 완료/재배치 처리를 담당한다.
 */
@Service
@Transactional(readOnly = true)
public class CalendarService {

    private final SchedulingEngine schedulingEngine;
    private final DailyScheduleRepository dailyScheduleRepository;
    private final ScheduleBlockRepository scheduleBlockRepository;
    private final BlockMetadataResolver metadataResolver;
    private final ReviewScheduleGenerator reviewScheduleGenerator;
    private final TodoRepository todoRepository;
    private final StudyUnitRepository studyUnitRepository;
    private final ReviewScheduleRepository reviewScheduleRepository;
    private final RoutineRepository routineRepository;
    private final RoutineCheckRepository routineCheckRepository;

    public CalendarService(
            SchedulingEngine schedulingEngine,
            DailyScheduleRepository dailyScheduleRepository,
            ScheduleBlockRepository scheduleBlockRepository,
            BlockMetadataResolver metadataResolver,
            ReviewScheduleGenerator reviewScheduleGenerator,
            TodoRepository todoRepository,
            StudyUnitRepository studyUnitRepository,
            ReviewScheduleRepository reviewScheduleRepository,
            RoutineRepository routineRepository,
            RoutineCheckRepository routineCheckRepository) {
        this.schedulingEngine = schedulingEngine;
        this.dailyScheduleRepository = dailyScheduleRepository;
        this.scheduleBlockRepository = scheduleBlockRepository;
        this.metadataResolver = metadataResolver;
        this.reviewScheduleGenerator = reviewScheduleGenerator;
        this.todoRepository = todoRepository;
        this.studyUnitRepository = studyUnitRepository;
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.routineRepository = routineRepository;
        this.routineCheckRepository = routineCheckRepository;
    }

    @Transactional
    public DailyScheduleResponse getDaily(Long memberId, LocalDate date) {
        SchedulingResult result = schedulingEngine.generate(memberId, date);
        DailySchedule schedule = result.dailySchedule();
        List<ScheduleBlock> sortedBlocks = schedule.getBlocks().stream()
                .sorted(Comparator.comparing(ScheduleBlock::getStartTime))
                .toList();
        Map<Long, BlockMetadata> metadata =
                metadataResolver.resolve(sortedBlocks);

        List<ScheduleBlockResponse> blockResponses = sortedBlocks.stream()
                .map(b -> toBlockResponse(b, metadata.get(b.getId())))
                .toList();
        List<WarningResponse> warnings = result.warnings().stream()
                .map(WarningResponse::from).toList();

        return new DailyScheduleResponse(
                schedule.getScheduleDate(),
                schedule.getTotalBlocks(),
                schedule.getCompletedBlocks(),
                blockResponses,
                warnings);
    }

    @Transactional
    public CompleteBlockResponse completeBlock(Long memberId, Long blockId) {
        ScheduleBlock block = loadOwnedBlock(memberId, blockId);
        if (block.getStatus() == BlockStatus.COMPLETED) {
            throw new CustomException(ErrorCode.INVALID_STATE);
        }

        BlockEffect effect = applyCompletionEffect(memberId, block);
        block.complete();

        DailySchedule schedule = block.getDailySchedule();
        int completed = (int) schedule.getBlocks().stream()
                .filter(b -> b.getStatus() == BlockStatus.COMPLETED)
                .count();
        schedule.markGenerated(schedule.getBlocks().size(), completed);

        return new CompleteBlockResponse(
                block.getId(),
                block.getStatus(),
                effect,
                new DailyProgress(schedule.getBlocks().size(), completed));
    }

    @Transactional
    public ReorderBlockResponse reorderBlock(Long memberId, Long blockId,
                                             ReorderBlockRequest request) {
        ScheduleBlock block = loadOwnedBlock(memberId, blockId);
        if (!request.endTime().isAfter(request.startTime())) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        if (hasTimeConflict(block, request)) {
            throw new CustomException(ErrorCode.INVALID_STATE);
        }
        block.reschedule(request.startTime(), request.endTime());
        return new ReorderBlockResponse(
                block.getId(),
                block.getStartTime(),
                block.getEndTime(),
                block.isPinned());
    }

    private ScheduleBlock loadOwnedBlock(Long memberId, Long blockId) {
        ScheduleBlock block = scheduleBlockRepository.findById(blockId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));
        if (!block.getDailySchedule().getMember().getId().equals(memberId)) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return block;
    }

    private boolean hasTimeConflict(ScheduleBlock target,
                                    ReorderBlockRequest request) {
        return target.getDailySchedule().getBlocks().stream()
                .filter(b -> !b.getId().equals(target.getId()))
                .anyMatch(b -> overlaps(
                        b.getStartTime(), b.getEndTime(),
                        request.startTime(), request.endTime()));
    }

    private boolean overlaps(java.time.LocalTime aStart, java.time.LocalTime aEnd,
                             java.time.LocalTime bStart, java.time.LocalTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    private BlockEffect applyCompletionEffect(Long memberId, ScheduleBlock block) {
        return switch (block.getBlockType()) {
            case FIXED -> BlockEffect.fixedCompleted();
            case ROUTINE -> completeRoutine(memberId, block);
            case TODO -> completeTodo(memberId, block);
            case STUDY -> completeStudy(memberId, block);
            case REVIEW -> completeReview(block);
        };
    }

    private BlockEffect completeRoutine(Long memberId, ScheduleBlock block) {
        Routine routine = routineRepository.findByIdAndMemberId(
                        block.getReferenceId(), memberId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));
        LocalDate checkDate = block.getDailySchedule().getScheduleDate();
        if (routineCheckRepository
                .findByRoutineIdAndCheckDate(routine.getId(), checkDate)
                .isEmpty()) {
            routineCheckRepository.save(new RoutineCheck(routine, checkDate));
        }
        return BlockEffect.streakUpdated();
    }

    private BlockEffect completeTodo(Long memberId, ScheduleBlock block) {
        Todo todo = todoRepository.findById(block.getReferenceId())
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));
        if (!todo.getMember().getId().equals(memberId)) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        todo.complete();
        return BlockEffect.todoCompleted();
    }

    private BlockEffect completeStudy(Long memberId, ScheduleBlock block) {
        StudyUnit unit = studyUnitRepository.findById(block.getReferenceId())
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));
        if (!unit.getMaterial().getMember().getId().equals(memberId)) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        unit.complete();
        LocalDate completedDate = block.getDailySchedule().getScheduleDate();
        ReviewScheduleGenerator.Result result = reviewScheduleGenerator
                .generate(memberId, unit, completedDate);
        if (result.count() == 0) {
            return BlockEffect.todoCompleted();
        }
        return BlockEffect.reviewCreated(
                result.firstReviewDate(), result.count());
    }

    private BlockEffect completeReview(ScheduleBlock block) {
        ReviewSchedule review = reviewScheduleRepository
                .findById(block.getReferenceId())
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));
        return BlockEffect.feedbackRequired(review.getId());
    }

    private ScheduleBlockResponse toBlockResponse(ScheduleBlock block,
                                                  BlockMetadata metadata) {
        BlockMetadata m = metadata != null ? metadata : BlockMetadata.unknown();
        return new ScheduleBlockResponse(
                block.getId(),
                block.getBlockType(),
                block.getReferenceId(),
                m.title(),
                m.categoryName(),
                m.categoryColor(),
                block.getStartTime(),
                block.getEndTime(),
                block.getStatus(),
                block.isPinned());
    }
}
