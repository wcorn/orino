package ds.project.orino.planner.scheduling.engine;

import ds.project.orino.domain.calendar.entity.BlockType;
import ds.project.orino.domain.material.entity.MaterialAllocation;
import ds.project.orino.domain.material.repository.MaterialAllocationRepository;
import ds.project.orino.domain.material.repository.MaterialDailyOverrideRepository;
import ds.project.orino.domain.preference.entity.StudyTimePreference;
import ds.project.orino.domain.preference.entity.UserPreference;
import ds.project.orino.planner.scheduling.engine.model.ItemCategory;
import ds.project.orino.planner.scheduling.engine.model.OverflowItem;
import ds.project.orino.planner.scheduling.engine.model.PlacementResult;
import ds.project.orino.planner.scheduling.engine.model.SchedulableItem;
import ds.project.orino.planner.scheduling.engine.model.TimeSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BlockPlacerTest {

    private BlockPlacer placer;

    @Mock private MaterialAllocationRepository allocationRepository;
    @Mock private MaterialDailyOverrideRepository overrideRepository;

    private UserPreference preference;
    private final LocalDate date = LocalDate.of(2026, 4, 10);

    @BeforeEach
    void setUp() {
        placer = new BlockPlacer(allocationRepository, overrideRepository);
        preference = new UserPreference(null);
        ReflectionTestUtils.setField(preference, "focusMinutes", 50);
        ReflectionTestUtils.setField(preference, "breakMinutes", 10);
        ReflectionTestUtils.setField(preference, "studyTimePreference",
                StudyTimePreference.MORNING);
    }

    @Test
    @DisplayName("단일 항목을 자유슬롯에 배치한다")
    void placeSingleItem() {
        List<TimeSlot> slots = List.of(slot(7, 0, 10, 0));
        SchedulableItem item = studyItem(30, 1L, null);

        PlacementResult result = placer.place(
                List.of(item), slots, preference, date);

        assertThat(result.placedBlocks()).hasSize(1);
        assertThat(result.overflows()).isEmpty();
        assertThat(result.placedBlocks().get(0).start())
                .isEqualTo(LocalTime.of(7, 0));
        assertThat(result.placedBlocks().get(0).end())
                .isEqualTo(LocalTime.of(7, 30));
    }

    @Test
    @DisplayName("focusMinutes보다 긴 항목은 집중시간 단위로 분할한다")
    void splitByFocusMinutes() {
        List<TimeSlot> slots = List.of(slot(7, 0, 10, 0));
        SchedulableItem item = studyItem(100, 1L, null);

        PlacementResult result = placer.place(
                List.of(item), slots, preference, date);

        assertThat(result.placedBlocks()).hasSize(2);
        assertThat(result.placedBlocks().get(0).start())
                .isEqualTo(LocalTime.of(7, 0));
        assertThat(result.placedBlocks().get(0).end())
                .isEqualTo(LocalTime.of(7, 50));
        // 10분 휴식 후
        assertThat(result.placedBlocks().get(1).start())
                .isEqualTo(LocalTime.of(8, 0));
        assertThat(result.placedBlocks().get(1).end())
                .isEqualTo(LocalTime.of(8, 50));
    }

    @Test
    @DisplayName("집중시간 청크가 맞는 슬롯이 없으면 overflow로 처리된다")
    void overflowOnSlotExhausted() {
        // focus=50, 슬롯은 40분 → 50분 청크가 들어가지 못함
        List<TimeSlot> slots = List.of(slot(7, 0, 7, 40));
        SchedulableItem item = studyItem(120, 1L, null);

        PlacementResult result = placer.place(
                List.of(item), slots, preference, date);

        assertThat(result.placedBlocks()).isEmpty();
        assertThat(result.overflows()).hasSize(1);
        assertThat(result.overflows().get(0).remainingMinutes())
                .isEqualTo(120);
        assertThat(result.overflows().get(0).reason())
                .isEqualTo(OverflowItem.OverflowReason.SLOT_EXHAUSTED);
    }

    @Test
    @DisplayName("나머지 시간이 focus보다 작으면 나머지만큼 배치한다")
    void lastChunkSmallerThanFocus() {
        List<TimeSlot> slots = List.of(slot(7, 0, 8, 0));
        SchedulableItem item = studyItem(20, 1L, null);

        PlacementResult result = placer.place(
                List.of(item), slots, preference, date);

        assertThat(result.placedBlocks()).hasSize(1);
        assertThat(result.placedBlocks().get(0).start())
                .isEqualTo(LocalTime.of(7, 0));
        assertThat(result.placedBlocks().get(0).end())
                .isEqualTo(LocalTime.of(7, 20));
    }

    @Test
    @DisplayName("학습 자료 할당시간을 초과하면 ALLOCATION_EXCEEDED overflow")
    void allocationCap() {
        MaterialAllocation allocation = new MaterialAllocation(null, 60);
        given(allocationRepository.findByMaterialId(10L))
                .willReturn(Optional.of(allocation));
        given(overrideRepository
                .findByMaterialIdAndOverrideDate(10L, date))
                .willReturn(Optional.empty());

        List<TimeSlot> slots = List.of(slot(7, 0, 12, 0));
        SchedulableItem item = studyItem(120, 1L, 10L);

        PlacementResult result = placer.place(
                List.of(item), slots, preference, date);

        // 할당은 60분 → 1 chunk 50min + 1 chunk 10min
        int placedMinutes = result.placedBlocks().stream()
                .mapToInt(b -> (int) java.time.Duration.between(
                        b.start(), b.end()).toMinutes())
                .sum();
        assertThat(placedMinutes).isEqualTo(60);
        assertThat(result.overflows()).hasSize(1);
        assertThat(result.overflows().get(0).remainingMinutes())
                .isEqualTo(60);
        assertThat(result.overflows().get(0).reason())
                .isEqualTo(OverflowItem.OverflowReason.ALLOCATION_EXCEEDED);
    }

    @Test
    @DisplayName("EVENING 선호는 가장 늦은 슬롯부터 채운다")
    void eveningPreference() {
        ReflectionTestUtils.setField(preference, "studyTimePreference",
                StudyTimePreference.EVENING);

        List<TimeSlot> slots = List.of(
                slot(7, 0, 8, 0), slot(20, 0, 22, 0));
        SchedulableItem item = studyItem(30, 1L, null);

        PlacementResult result = placer.place(
                List.of(item), slots, preference, date);

        assertThat(result.placedBlocks().get(0).start())
                .isEqualTo(LocalTime.of(20, 0));
    }

    private TimeSlot slot(int startH, int startM, int endH, int endM) {
        return new TimeSlot(
                LocalTime.of(startH, startM), LocalTime.of(endH, endM));
    }

    private SchedulableItem studyItem(int minutes, long refId,
                                      Long materialId) {
        return SchedulableItem.builder()
                .category(ItemCategory.NEW_STUDY)
                .blockType(BlockType.STUDY)
                .referenceId(refId)
                .estimatedMinutes(minutes)
                .materialId(materialId)
                .title("study-" + refId)
                .build();
    }
}
