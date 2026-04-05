package ds.project.orino.planner.scheduling.engine;

import ds.project.orino.domain.material.entity.MaterialAllocation;
import ds.project.orino.domain.material.entity.MaterialDailyOverride;
import ds.project.orino.domain.material.repository.MaterialAllocationRepository;
import ds.project.orino.domain.material.repository.MaterialDailyOverrideRepository;
import ds.project.orino.domain.preference.entity.StudyTimePreference;
import ds.project.orino.domain.preference.entity.UserPreference;
import ds.project.orino.planner.scheduling.engine.model.OverflowItem;
import ds.project.orino.planner.scheduling.engine.model.OverflowItem.OverflowReason;
import ds.project.orino.planner.scheduling.engine.model.PlacedBlock;
import ds.project.orino.planner.scheduling.engine.model.PlacementResult;
import ds.project.orino.planner.scheduling.engine.model.SchedulableItem;
import ds.project.orino.planner.scheduling.engine.model.TimeSlot;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 우선순위 정렬된 항목을 자유 시간 블록에 배치한다.
 * 집중시간/휴식, 선호 시간대, 학습 자료별 시간 할당을 반영한다.
 */
@Component
public class BlockPlacer {

    private final MaterialAllocationRepository allocationRepository;
    private final MaterialDailyOverrideRepository overrideRepository;

    public BlockPlacer(MaterialAllocationRepository allocationRepository,
                       MaterialDailyOverrideRepository overrideRepository) {
        this.allocationRepository = allocationRepository;
        this.overrideRepository = overrideRepository;
    }

    public PlacementResult place(List<SchedulableItem> items,
                                 List<TimeSlot> initialSlots,
                                 UserPreference preference,
                                 LocalDate date) {
        int focus = Math.max(1, preference.getFocusMinutes());
        int breakMin = Math.max(0, preference.getBreakMinutes());
        StudyTimePreference timePref =
                preference.getStudyTimePreference();

        List<MutableSlot> slots = toMutable(initialSlots);
        Map<Long, Integer> allocationCaps = new HashMap<>();
        Map<Long, Integer> allocationUsed = new HashMap<>();

        List<PlacedBlock> placed = new ArrayList<>();
        List<OverflowItem> overflows = new ArrayList<>();

        for (SchedulableItem item : items) {
            Integer cap = resolveAllocationCap(item.materialId(), date,
                    allocationCaps);
            int used = allocationUsed.getOrDefault(
                    Optional.ofNullable(item.materialId()).orElse(-1L), 0);
            int remaining = item.estimatedMinutes();
            if (cap != null) {
                int allowed = Math.max(0, cap - used);
                if (allowed == 0) {
                    overflows.add(new OverflowItem(item, remaining,
                            OverflowReason.ALLOCATION_EXCEEDED));
                    continue;
                }
                if (remaining > allowed) {
                    overflows.add(new OverflowItem(item,
                            remaining - allowed,
                            OverflowReason.ALLOCATION_EXCEEDED));
                    remaining = allowed;
                }
            }

            int placedMinutes = 0;
            while (remaining > 0) {
                int chunkSize = Math.min(focus, remaining);
                MutableSlot slot = findSlotFitting(slots, chunkSize,
                        timePref);
                if (slot == null) {
                    overflows.add(new OverflowItem(item, remaining,
                            OverflowReason.SLOT_EXHAUSTED));
                    break;
                }
                LocalTime start = slot.start;
                LocalTime end = start.plusMinutes(chunkSize);
                placed.add(new PlacedBlock(
                        item.blockType(), item.referenceId(),
                        start, end, item.title()));
                LocalTime newStart = end.plusMinutes(breakMin);
                if (!newStart.isBefore(slot.end)) {
                    slot.start = slot.end;
                } else {
                    slot.start = newStart;
                }
                remaining -= chunkSize;
                placedMinutes += chunkSize;
            }

            if (placedMinutes > 0 && item.materialId() != null) {
                allocationUsed.merge(item.materialId(), placedMinutes,
                        Integer::sum);
            }
        }

        return new PlacementResult(placed, overflows);
    }

    private List<MutableSlot> toMutable(List<TimeSlot> slots) {
        List<MutableSlot> list = new ArrayList<>();
        for (TimeSlot s : slots) {
            list.add(new MutableSlot(s.start(), s.end()));
        }
        return list;
    }

    private MutableSlot findSlotFitting(List<MutableSlot> slots,
                                        int minutes,
                                        StudyTimePreference pref) {
        List<MutableSlot> fitting = slots.stream()
                .filter(s -> s.remaining() >= minutes)
                .sorted(preferenceComparator(pref))
                .toList();
        if (fitting.isEmpty()) {
            return null;
        }
        return fitting.get(0);
    }

    private Comparator<MutableSlot> preferenceComparator(
            StudyTimePreference pref) {
        return switch (pref) {
            case EVENING -> Comparator.comparing(
                    (MutableSlot s) -> s.start).reversed();
            case MORNING, AFTERNOON -> Comparator.comparing(
                    (MutableSlot s) -> s.start);
        };
    }

    private Integer resolveAllocationCap(Long materialId, LocalDate date,
                                         Map<Long, Integer> cache) {
        if (materialId == null) {
            return null;
        }
        if (cache.containsKey(materialId)) {
            return cache.get(materialId);
        }
        Optional<MaterialDailyOverride> override = overrideRepository
                .findByMaterialIdAndOverrideDate(materialId, date);
        if (override.isPresent()) {
            int minutes = override.get().getMinutes();
            cache.put(materialId, minutes);
            return minutes;
        }
        Optional<MaterialAllocation> allocation = allocationRepository
                .findByMaterialId(materialId);
        Integer cap = allocation
                .map(MaterialAllocation::getDefaultMinutes)
                .orElse(null);
        cache.put(materialId, cap);
        return cap;
    }

    private static final class MutableSlot {
        LocalTime start;
        final LocalTime end;

        MutableSlot(LocalTime start, LocalTime end) {
            this.start = start;
            this.end = end;
        }

        int remaining() {
            if (!start.isBefore(end)) {
                return 0;
            }
            return (int) java.time.Duration.between(start, end).toMinutes();
        }
    }
}
