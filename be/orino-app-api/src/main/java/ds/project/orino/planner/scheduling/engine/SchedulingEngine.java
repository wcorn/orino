package ds.project.orino.planner.scheduling.engine;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.calendar.entity.BlockStatus;
import ds.project.orino.domain.calendar.entity.DailySchedule;
import ds.project.orino.domain.calendar.entity.ScheduleBlock;
import ds.project.orino.domain.calendar.repository.DailyScheduleRepository;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.preference.entity.UserPreference;
import ds.project.orino.domain.preference.repository.UserPreferenceRepository;
import ds.project.orino.planner.scheduling.engine.model.AvailabilityResult;
import ds.project.orino.planner.scheduling.engine.model.PlacedBlock;
import ds.project.orino.planner.scheduling.engine.model.PlacementResult;
import ds.project.orino.planner.scheduling.engine.model.SchedulableItem;
import ds.project.orino.planner.scheduling.engine.model.SchedulingResult;
import ds.project.orino.planner.scheduling.engine.model.SchedulingWarning;
import ds.project.orino.planner.scheduling.engine.model.TimeSlot;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 스케줄링 엔진. 5단계 파이프라인을 오케스트레이션하고 DailySchedule을 저장한다.
 *
 * 재생성 시 COMPLETED 또는 pinned 블록은 유지하고, 나머지만 재배치한다.
 * 과거 날짜는 재생성하지 않는다.
 */
@Component
public class SchedulingEngine {

    private final DailyScheduleRepository dailyScheduleRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final MemberRepository memberRepository;
    private final AvailabilityCalculator availabilityCalculator;
    private final ItemCollector itemCollector;
    private final ItemPrioritizer itemPrioritizer;
    private final BlockPlacer blockPlacer;
    private final OverflowHandler overflowHandler;

    public SchedulingEngine(
            DailyScheduleRepository dailyScheduleRepository,
            UserPreferenceRepository preferenceRepository,
            MemberRepository memberRepository,
            AvailabilityCalculator availabilityCalculator,
            ItemCollector itemCollector,
            ItemPrioritizer itemPrioritizer,
            BlockPlacer blockPlacer,
            OverflowHandler overflowHandler) {
        this.dailyScheduleRepository = dailyScheduleRepository;
        this.preferenceRepository = preferenceRepository;
        this.memberRepository = memberRepository;
        this.availabilityCalculator = availabilityCalculator;
        this.itemCollector = itemCollector;
        this.itemPrioritizer = itemPrioritizer;
        this.blockPlacer = blockPlacer;
        this.overflowHandler = overflowHandler;
    }

    @Transactional
    public SchedulingResult generate(Long memberId, LocalDate date) {
        LocalDate today = LocalDate.now();
        DailySchedule dailySchedule = dailyScheduleRepository
                .findByMemberIdAndScheduleDate(memberId, date)
                .orElseGet(() -> createDailySchedule(memberId, date));

        if (date.isBefore(today)) {
            dailySchedule.getBlocks().size();
            return new SchedulingResult(dailySchedule, List.of());
        }

        if (!dailySchedule.isDirty()) {
            dailySchedule.getBlocks().size();
            return new SchedulingResult(dailySchedule, List.of());
        }

        UserPreference preference = preferenceRepository
                .findByMemberId(memberId)
                .orElseGet(() -> createDefaultPreference(memberId));

        List<ScheduleBlock> lockedBlocks = extractLockedBlocks(dailySchedule);
        dailySchedule.clearBlocks();
        reinsertLocked(dailySchedule, lockedBlocks);

        AvailabilityResult availability = availabilityCalculator
                .calculate(memberId, date, preference);
        List<TimeSlot> freeSlots = subtractLockedFromFreeSlots(
                availability.freeSlots(), lockedBlocks);

        Set<ItemKey> locked = lockedReferenceKeys(lockedBlocks);

        List<SchedulableItem> collected = itemCollector.collect(memberId, date);
        List<SchedulableItem> remaining = collected.stream()
                .filter(i -> !locked.contains(
                        new ItemKey(i.blockType(), i.referenceId())))
                .toList();
        List<SchedulableItem> prioritized = itemPrioritizer.prioritize(remaining);

        PlacementResult placement = blockPlacer.place(
                prioritized, freeSlots, preference, date);

        List<ScheduleBlock> newBlocks = new ArrayList<>();
        newBlocks.addAll(toScheduleBlocks(dailySchedule,
                availability.preplacedBlocks(), nextSortOrder(dailySchedule)));
        newBlocks.addAll(toScheduleBlocks(dailySchedule,
                placement.placedBlocks(),
                nextSortOrder(dailySchedule) + availability.preplacedBlocks().size()));
        for (ScheduleBlock block : newBlocks) {
            dailySchedule.addBlock(block);
        }

        resortBlocks(dailySchedule);

        int total = dailySchedule.getBlocks().size();
        int completed = (int) dailySchedule.getBlocks().stream()
                .filter(b -> b.getStatus() == BlockStatus.COMPLETED)
                .count();
        dailySchedule.markGenerated(total, completed);

        List<SchedulingWarning> warnings = overflowHandler.handle(
                placement.overflows(), date);

        return new SchedulingResult(dailySchedule, warnings);
    }

    private DailySchedule createDailySchedule(Long memberId, LocalDate date) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));
        return dailyScheduleRepository.save(
                new DailySchedule(member, date));
    }

    private UserPreference createDefaultPreference(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));
        return preferenceRepository.save(new UserPreference(member));
    }

    private List<ScheduleBlock> extractLockedBlocks(DailySchedule schedule) {
        return schedule.getBlocks().stream()
                .filter(ScheduleBlock::isLocked)
                .toList();
    }

    private void reinsertLocked(DailySchedule schedule,
                                List<ScheduleBlock> locked) {
        for (ScheduleBlock block : locked) {
            schedule.addBlock(block);
        }
    }

    private List<TimeSlot> subtractLockedFromFreeSlots(
            List<TimeSlot> freeSlots, List<ScheduleBlock> locked) {
        if (locked.isEmpty()) {
            return freeSlots;
        }
        List<TimeSlot> taken = locked.stream()
                .map(b -> new TimeSlot(b.getStartTime(), b.getEndTime()))
                .sorted(Comparator.comparing(TimeSlot::start))
                .toList();
        List<TimeSlot> result = new ArrayList<>();
        for (TimeSlot slot : freeSlots) {
            result.addAll(subtractFromSlot(slot, taken));
        }
        return result;
    }

    private List<TimeSlot> subtractFromSlot(TimeSlot slot,
                                            List<TimeSlot> taken) {
        List<TimeSlot> result = new ArrayList<>();
        LocalTime cursor = slot.start();
        for (TimeSlot t : taken) {
            if (!t.start().isBefore(slot.end())
                    || !t.end().isAfter(slot.start())) {
                continue;
            }
            LocalTime tStart = t.start().isAfter(cursor)
                    ? t.start() : cursor;
            if (tStart.isAfter(cursor)) {
                result.add(new TimeSlot(cursor, tStart));
            }
            if (t.end().isAfter(cursor)) {
                cursor = t.end();
            }
        }
        if (cursor.isBefore(slot.end())) {
            result.add(new TimeSlot(cursor, slot.end()));
        }
        return result;
    }

    private Set<ItemKey> lockedReferenceKeys(List<ScheduleBlock> locked) {
        Set<ItemKey> set = new HashSet<>();
        for (ScheduleBlock block : locked) {
            set.add(new ItemKey(block.getBlockType(),
                    block.getReferenceId()));
        }
        return set;
    }

    private List<ScheduleBlock> toScheduleBlocks(DailySchedule schedule,
                                                 List<PlacedBlock> placed,
                                                 int startingSortOrder) {
        List<ScheduleBlock> blocks = new ArrayList<>();
        int order = startingSortOrder;
        List<PlacedBlock> sorted = placed.stream()
                .sorted(Comparator.comparing(PlacedBlock::start))
                .toList();
        for (PlacedBlock p : sorted) {
            blocks.add(new ScheduleBlock(schedule, p.blockType(),
                    p.referenceId(), p.start(), p.end(), order++));
        }
        return blocks;
    }

    private int nextSortOrder(DailySchedule schedule) {
        return schedule.getBlocks().stream()
                .mapToInt(ScheduleBlock::getSortOrder)
                .max()
                .orElse(-1) + 1;
    }

    private void resortBlocks(DailySchedule schedule) {
        List<ScheduleBlock> sorted = schedule.getBlocks().stream()
                .sorted(Comparator.comparing(ScheduleBlock::getStartTime))
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).updateSortOrder(i);
        }
    }

    private record ItemKey(
            ds.project.orino.domain.calendar.entity.BlockType type,
            Long referenceId) {
    }
}
