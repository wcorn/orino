package ds.project.orino.planner.calendar.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.calendar.entity.BlockStatus;
import ds.project.orino.domain.calendar.entity.BlockType;
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
import ds.project.orino.planner.calendar.dto.MonthlyDayResponse;
import ds.project.orino.planner.calendar.dto.MonthlyScheduleResponse;
import ds.project.orino.planner.calendar.dto.PostponeBlockRequest;
import ds.project.orino.planner.calendar.dto.PostponeBlockResponse;
import ds.project.orino.planner.calendar.dto.PostponeStrategy;
import ds.project.orino.planner.calendar.dto.ReorderBlockRequest;
import ds.project.orino.planner.calendar.dto.ReorderBlockResponse;
import ds.project.orino.planner.calendar.dto.ScheduleBlockResponse;
import ds.project.orino.planner.calendar.dto.WarningResponse;
import ds.project.orino.planner.calendar.dto.WeeklyDayResponse;
import ds.project.orino.planner.calendar.dto.WeeklyScheduleResponse;
import ds.project.orino.planner.scheduling.dirty.DirtyScheduleMarker;
import ds.project.orino.planner.scheduling.engine.SchedulingEngine;
import ds.project.orino.planner.scheduling.engine.model.SchedulingResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final DirtyScheduleMarker dirtyScheduleMarker;

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
            RoutineCheckRepository routineCheckRepository,
            DirtyScheduleMarker dirtyScheduleMarker) {
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
        this.dirtyScheduleMarker = dirtyScheduleMarker;
    }

    @Transactional
    public DailyScheduleResponse getDaily(Long memberId, LocalDate date) {
        SchedulingResult result = schedulingEngine.generate(memberId, date);
        DailySchedule schedule = result.dailySchedule();
        List<ScheduleBlock> visibleBlocks = schedule.getBlocks().stream()
                .filter(b -> b.getStatus() != BlockStatus.POSTPONED)
                .sorted(Comparator.comparing(ScheduleBlock::getStartTime))
                .toList();
        Map<Long, BlockMetadata> metadata =
                metadataResolver.resolve(visibleBlocks);

        List<ScheduleBlockResponse> blockResponses = visibleBlocks.stream()
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
    public WeeklyScheduleResponse getWeekly(Long memberId, LocalDate startDate) {
        LocalDate endDate = startDate.plusDays(6);
        List<WeeklyDayResponse> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate date = startDate.plusDays(i);
            SchedulingResult result = schedulingEngine.generate(memberId, date);
            DailySchedule schedule = result.dailySchedule();
            List<ScheduleBlock> visibleBlocks = schedule.getBlocks().stream()
                    .filter(b -> b.getStatus() != BlockStatus.POSTPONED)
                    .sorted(Comparator.comparing(ScheduleBlock::getStartTime))
                    .toList();
            Map<Long, BlockMetadata> metadata =
                    metadataResolver.resolve(visibleBlocks);
            List<ScheduleBlockResponse> blockResponses = visibleBlocks.stream()
                    .map(b -> toBlockResponse(b, metadata.get(b.getId())))
                    .toList();
            days.add(new WeeklyDayResponse(
                    date,
                    schedule.getTotalBlocks(),
                    schedule.getCompletedBlocks(),
                    blockResponses));
        }
        return new WeeklyScheduleResponse(startDate, endDate, days);
    }

    @Transactional
    public MonthlyScheduleResponse getMonthly(Long memberId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate firstDay = yearMonth.atDay(1);
        LocalDate lastDay = yearMonth.atEndOfMonth();
        List<MonthlyDayResponse> days = new ArrayList<>();
        for (LocalDate date = firstDay; !date.isAfter(lastDay);
                date = date.plusDays(1)) {
            SchedulingResult result = schedulingEngine.generate(memberId, date);
            DailySchedule schedule = result.dailySchedule();
            Set<BlockType> distinctTypes = new LinkedHashSet<>();
            for (ScheduleBlock block : schedule.getBlocks()) {
                if (block.getStatus() != BlockStatus.POSTPONED) {
                    distinctTypes.add(block.getBlockType());
                }
            }
            days.add(new MonthlyDayResponse(
                    date,
                    schedule.getTotalBlocks(),
                    schedule.getCompletedBlocks(),
                    new ArrayList<>(distinctTypes)));
        }
        return new MonthlyScheduleResponse(year, month, days);
    }

    @Transactional
    public CompleteBlockResponse completeBlock(Long memberId, Long blockId) {
        ScheduleBlock block = loadOwnedBlock(memberId, blockId);
        if (block.getStatus() != BlockStatus.SCHEDULED) {
            throw new CustomException(ErrorCode.INVALID_STATE);
        }

        BlockEffect effect = applyCompletionEffect(memberId, block);
        block.complete();

        DailySchedule schedule = block.getDailySchedule();
        int total = countActiveBlocks(schedule);
        int completed = countCompletedBlocks(schedule);
        schedule.markGenerated(total, completed);

        return new CompleteBlockResponse(
                block.getId(),
                block.getStatus(),
                effect,
                new DailyProgress(total, completed));
    }

    @Transactional
    public PostponeBlockResponse postponeBlock(
            Long memberId, Long blockId, PostponeBlockRequest request) {
        ScheduleBlock block = loadOwnedBlock(memberId, blockId);
        if (block.getStatus() != BlockStatus.SCHEDULED) {
            throw new CustomException(ErrorCode.INVALID_STATE);
        }
        if (block.getBlockType() == BlockType.FIXED
                || block.getBlockType() == BlockType.ROUTINE) {
            throw new CustomException(ErrorCode.INVALID_STATE);
        }

        LocalDate sourceDate = block.getDailySchedule().getScheduleDate();
        LocalDate targetDate = resolveTargetDate(
                memberId, sourceDate, request.strategy());

        block.postpone();
        if (block.getBlockType() == BlockType.REVIEW) {
            ReviewSchedule review = reviewScheduleRepository
                    .findById(block.getReferenceId())
                    .orElseThrow(() -> new CustomException(
                            ErrorCode.RESOURCE_NOT_FOUND));
            review.reschedule(targetDate);
        }

        DailySchedule sourceSchedule = block.getDailySchedule();
        int total = countActiveBlocks(sourceSchedule);
        int completed = countCompletedBlocks(sourceSchedule);
        sourceSchedule.markGenerated(total, completed);

        dirtyScheduleMarker.markDirtyOn(memberId, targetDate);

        return new PostponeBlockResponse(
                block.getId(), targetDate,
                new DailyProgress(total, completed));
    }

    private LocalDate resolveTargetDate(Long memberId, LocalDate sourceDate,
                                        PostponeStrategy strategy) {
        if (strategy == PostponeStrategy.TOMORROW) {
            return sourceDate.plusDays(1);
        }
        LocalDate rangeStart = sourceDate.plusDays(1);
        LocalDate rangeEnd = sourceDate.plusDays(6);
        List<DailySchedule> existing = dailyScheduleRepository
                .findByMemberIdAndScheduleDateBetween(
                        memberId, rangeStart, rangeEnd);
        Map<LocalDate, Integer> counts = new java.util.HashMap<>();
        for (DailySchedule s : existing) {
            counts.put(s.getScheduleDate(), countActiveBlocks(s));
        }
        LocalDate best = rangeStart;
        int bestCount = counts.getOrDefault(rangeStart, 0);
        for (LocalDate d = rangeStart.plusDays(1); !d.isAfter(rangeEnd);
                d = d.plusDays(1)) {
            int c = counts.getOrDefault(d, 0);
            if (c < bestCount) {
                best = d;
                bestCount = c;
            }
        }
        return best;
    }

    private int countActiveBlocks(DailySchedule schedule) {
        return (int) schedule.getBlocks().stream()
                .filter(b -> b.getStatus() != BlockStatus.POSTPONED)
                .count();
    }

    private int countCompletedBlocks(DailySchedule schedule) {
        return (int) schedule.getBlocks().stream()
                .filter(b -> b.getStatus() == BlockStatus.COMPLETED)
                .count();
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
