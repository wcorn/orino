package ds.project.orino.planner.material.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.category.entity.Category;
import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.domain.goal.entity.Goal;
import ds.project.orino.domain.goal.entity.PeriodType;
import ds.project.orino.domain.goal.repository.GoalRepository;
import ds.project.orino.domain.material.entity.DeadlineMode;
import ds.project.orino.domain.material.entity.MaterialAllocation;
import ds.project.orino.domain.material.entity.MaterialDailyOverride;
import ds.project.orino.domain.material.entity.MaterialStatus;
import ds.project.orino.domain.material.entity.MaterialType;
import ds.project.orino.domain.material.entity.MissedPolicy;
import ds.project.orino.domain.material.entity.ReviewConfig;
import ds.project.orino.domain.material.entity.StudyMaterial;
import ds.project.orino.domain.material.entity.StudyUnit;
import ds.project.orino.domain.material.repository.MaterialAllocationRepository;
import ds.project.orino.domain.material.repository.MaterialDailyOverrideRepository;
import ds.project.orino.domain.material.repository.ReviewConfigRepository;
import ds.project.orino.domain.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.material.repository.StudyUnitRepository;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.preference.repository.UserPreferenceRepository;
import ds.project.orino.planner.material.dto.AllocationRequest;
import ds.project.orino.planner.material.dto.AllocationResponse;
import ds.project.orino.planner.material.dto.CreateMaterialRequest;
import ds.project.orino.planner.material.dto.CreateUnitBatchRequest;
import ds.project.orino.planner.material.dto.CreateUnitRequest;
import ds.project.orino.planner.material.dto.DailyOverrideRequest;
import ds.project.orino.planner.material.dto.DailyOverrideResponse;
import ds.project.orino.planner.material.dto.MaterialDetailResponse;
import ds.project.orino.planner.material.dto.MaterialResponse;
import ds.project.orino.planner.material.dto.ReviewConfigRequest;
import ds.project.orino.planner.material.dto.ReviewConfigResponse;
import ds.project.orino.planner.material.dto.UnitResponse;
import ds.project.orino.planner.material.dto.UpdateMaterialRequest;
import ds.project.orino.planner.material.dto.UpdateUnitRequest;
import ds.project.orino.planner.scheduling.dirty.DirtyScheduleMarker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    private MaterialService materialService;

    @Mock private StudyMaterialRepository materialRepository;
    @Mock private StudyUnitRepository unitRepository;
    @Mock private MaterialAllocationRepository allocationRepository;
    @Mock private MaterialDailyOverrideRepository dailyOverrideRepository;
    @Mock private ReviewConfigRepository reviewConfigRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private UserPreferenceRepository preferenceRepository;
    @Mock private DirtyScheduleMarker dirtyScheduleMarker;
    private DeadlineCalculator deadlineCalculator;

    private Member member;

    @BeforeEach
    void setUp() {
        deadlineCalculator = new DeadlineCalculator();
        materialService = new MaterialService(
                materialRepository, unitRepository,
                allocationRepository, dailyOverrideRepository,
                reviewConfigRepository, memberRepository,
                categoryRepository, goalRepository,
                preferenceRepository, deadlineCalculator,
                dirtyScheduleMarker);
        member = new Member("admin", "encoded");
    }

    private StudyMaterial createMaterial() {
        return new StudyMaterial(
                member, "이펙티브 자바", MaterialType.BOOK,
                null, null,
                LocalDate.of(2026, 7, 1), DeadlineMode.DEADLINE);
    }

    // --- Material CRUD ---

    @Test
    @DisplayName("학습 자료 목록을 조회한다")
    void getMaterials() {
        StudyMaterial material = createMaterial();
        given(materialRepository
                .findByMemberIdOrderByCreatedAtDesc(1L))
                .willReturn(List.of(material));

        List<MaterialResponse> result =
                materialService.getMaterials(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("이펙티브 자바");
    }

    @Test
    @DisplayName("학습 자료 상세를 조회한다")
    void getMaterial() {
        StudyMaterial material = createMaterial();
        given(materialRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(material));

        MaterialDetailResponse result =
                materialService.getMaterial(1L, 1L);

        assertThat(result.title()).isEqualTo("이펙티브 자바");
        assertThat(result.type()).isEqualTo(MaterialType.BOOK);
    }

    @Test
    @DisplayName("학습 자료를 생성한다")
    void create() {
        StudyMaterial saved = createMaterial();
        given(memberRepository.findById(1L))
                .willReturn(Optional.of(member));
        given(materialRepository.save(any(StudyMaterial.class)))
                .willReturn(saved);

        MaterialResponse result = materialService.create(1L,
                new CreateMaterialRequest(
                        "이펙티브 자바", MaterialType.BOOK,
                        null, null,
                        LocalDate.of(2026, 7, 1),
                        DeadlineMode.DEADLINE, null));

        assertThat(result.title()).isEqualTo("이펙티브 자바");
        assertThat(result.deadlineMode()).isEqualTo(DeadlineMode.DEADLINE);
    }

    @Test
    @DisplayName("카테고리와 목표를 지정하여 학습 자료를 생성한다")
    void create_withCategoryAndGoal() {
        Category category = new Category(
                member, "프로그래밍", "#FF9800", "code", 0);
        Goal goal = new Goal(
                member, null, "백엔드 취업", null,
                PeriodType.YEAR,
                LocalDate.of(2026, 1, 1), null);

        given(memberRepository.findById(1L))
                .willReturn(Optional.of(member));
        given(categoryRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(category));
        given(goalRepository.findByIdAndMemberId(2L, 1L))
                .willReturn(Optional.of(goal));
        given(materialRepository.save(any(StudyMaterial.class)))
                .willReturn(createMaterial());

        materialService.create(1L,
                new CreateMaterialRequest(
                        "이펙티브 자바", MaterialType.BOOK,
                        1L, 2L, null, null, null));

        verify(categoryRepository).findByIdAndMemberId(1L, 1L);
        verify(goalRepository).findByIdAndMemberId(2L, 1L);
    }

    @Test
    @DisplayName("인라인 ��습 단위와 함께 생성한다")
    void create_withUnits() {
        given(memberRepository.findById(1L))
                .willReturn(Optional.of(member));
        given(materialRepository.save(any(StudyMaterial.class)))
                .willAnswer(inv -> inv.getArgument(0));

        List<CreateUnitRequest> units = List.of(
                new CreateUnitRequest("아이템 1", 30, null, 0),
                new CreateUnitRequest("아이템 2", 30, null, 1));

        MaterialResponse result = materialService.create(1L,
                new CreateMaterialRequest(
                        "이펙티브 자바", MaterialType.BOOK,
                        null, null, null, null, units));

        assertThat(result.totalUnits()).isEqualTo(2);
    }

    @Test
    @DisplayName("존재하지 않는 회원으로 생성 시 예외를 던진다")
    void create_memberNotFound() {
        given(memberRepository.findById(999L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> materialService.create(999L,
                new CreateMaterialRequest(
                        "테스트", MaterialType.BOOK,
                        null, null, null, null, null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(
                        ((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("학습 자료를 수정한다")
    void update() {
        StudyMaterial material = createMaterial();
        given(materialRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(material));

        MaterialResponse result = materialService.update(1L, 1L,
                new UpdateMaterialRequest(
                        "수정된 제목", MaterialType.LECTURE,
                        null, null, null, DeadlineMode.FREE));

        assertThat(result.title()).isEqualTo("수정된 제목");
        assertThat(result.type()).isEqualTo(MaterialType.LECTURE);
    }

    @Test
    @DisplayName("학습 자료를 삭제한다")
    void delete() {
        StudyMaterial material = createMaterial();
        given(materialRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(material));

        materialService.delete(1L, 1L);

        verify(materialRepository).delete(material);
    }

    @Test
    @DisplayName("학습 자료를 일시정지한다")
    void pause() {
        StudyMaterial material = createMaterial();
        given(materialRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(material));

        MaterialResponse result = materialService.pause(1L, 1L);

        assertThat(result.status()).isEqualTo(MaterialStatus.PAUSED);
    }

    @Test
    @DisplayName("PAUSED 상태가 아닌 자료를 resume하면 예외를 던진다")
    void resume_invalidState() {
        StudyMaterial material = createMaterial();
        given(materialRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(material));

        assertThatThrownBy(
                () -> materialService.resume(1L, 1L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(
                        ((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_STATE));
    }

    @Test
    @DisplayName("일시정지된 자료를 재개한다")
    void resume() {
        StudyMaterial material = createMaterial();
        material.pause();
        given(materialRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(material));

        MaterialResponse result = materialService.resume(1L, 1L);

        assertThat(result.status()).isEqualTo(MaterialStatus.ACTIVE);
    }

    // --- Study Unit ---

    @Test
    @DisplayName("학습 단위를 추가한다")
    void createUnit() {
        StudyMaterial material = createMaterial();
        StudyUnit saved = new StudyUnit(
                material, "아이템 1", 0, 30, null);

        given(materialRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(material));
        given(unitRepository.save(any(StudyUnit.class)))
                .willReturn(saved);

        UnitResponse result = materialService.createUnit(
                1L, 1L,
                new CreateUnitRequest("아이템 1", 30, null, 0));

        assertThat(result.title()).isEqualTo("아이템 1");
        assertThat(result.estimatedMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("학습 단위를 일괄 등록한다")
    void createUnitsBatch() {
        StudyMaterial material = createMaterial();
        StudyUnit unit1 = new StudyUnit(
                material, "아이템 1", 0, 30, null);
        StudyUnit unit2 = new StudyUnit(
                material, "아이템 2", 1, 45, null);

        given(materialRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(material));
        given(unitRepository.save(any(StudyUnit.class)))
                .willReturn(unit1).willReturn(unit2);

        List<UnitResponse> result =
                materialService.createUnitsBatch(1L, 1L,
                        new CreateUnitBatchRequest(List.of(
                                new CreateUnitRequest(
                                        "아이템 1", 30, null, 0),
                                new CreateUnitRequest(
                                        "아이템 2", 45, null, 1))));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("학습 단위를 수정한다")
    void updateUnit() {
        StudyMaterial material = createMaterial();
        StudyUnit unit = new StudyUnit(
                material, "기존", 0, 30, null);

        given(materialRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(material));
        given(unitRepository.findByIdAndMaterialId(1L, 1L))
                .willReturn(Optional.of(unit));

        UnitResponse result = materialService.updateUnit(
                1L, 1L, 1L,
                new UpdateUnitRequest("수정됨", 60, null, 2));

        assertThat(result.title()).isEqualTo("수정됨");
        assertThat(result.estimatedMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("학습 단위를 삭제한다")
    void deleteUnit() {
        StudyMaterial material = createMaterial();
        StudyUnit unit = new StudyUnit(
                material, "삭제대상", 0, 30, null);

        given(materialRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(material));
        given(unitRepository.findByIdAndMaterialId(1L, 1L))
                .willReturn(Optional.of(unit));

        materialService.deleteUnit(1L, 1L, 1L);

        verify(unitRepository).delete(unit);
    }

    // --- Allocation ---

    @Test
    @DisplayName("시간 할당을 설정한다 (신규)")
    void updateAllocation_new() {
        StudyMaterial material = createMaterial();
        given(materialRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(material));
        given(allocationRepository.findByMaterialId(1L))
                .willReturn(Optional.empty());

        AllocationResponse result =
                materialService.updateAllocation(1L, 1L,
                        new AllocationRequest(60));

        verify(allocationRepository)
                .save(any(MaterialAllocation.class));
    }

    @Test
    @DisplayName("시간 할당을 수정한다 (기존)")
    void updateAllocation_existing() {
        StudyMaterial material = createMaterial();
        MaterialAllocation existing =
                new MaterialAllocation(material, 30);

        given(materialRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(material));
        given(allocationRepository.findByMaterialId(1L))
                .willReturn(Optional.of(existing));

        AllocationResponse result =
                materialService.updateAllocation(1L, 1L,
                        new AllocationRequest(90));

        assertThat(result.defaultMinutes()).isEqualTo(90);
    }

    // --- Daily Override ---

    @Test
    @DisplayName("일별 오버라이드 목록을 조회한다")
    void getDailyOverrides() {
        StudyMaterial material = createMaterial();
        LocalDate date = LocalDate.of(2026, 4, 10);
        MaterialDailyOverride override =
                new MaterialDailyOverride(material, date, 120);

        given(materialRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(material));
        given(dailyOverrideRepository
                .findByMaterialIdOrderByOverrideDate(1L))
                .willReturn(List.of(override));

        List<DailyOverrideResponse> result =
                materialService.getDailyOverrides(1L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).minutes()).isEqualTo(120);
    }

    @Test
    @DisplayName("일별 오버라이드를 설정한다")
    void updateDailyOverride() {
        StudyMaterial material = createMaterial();
        LocalDate date = LocalDate.of(2026, 4, 10);

        given(materialRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(material));
        given(dailyOverrideRepository
                .findByMaterialIdAndOverrideDate(1L, date))
                .willReturn(Optional.empty());

        DailyOverrideResponse result =
                materialService.updateDailyOverride(
                        1L, 1L, date,
                        new DailyOverrideRequest(120));

        verify(dailyOverrideRepository)
                .save(any(MaterialDailyOverride.class));
    }

    @Test
    @DisplayName("일별 오버라이드를 삭제한다")
    void deleteDailyOverride() {
        StudyMaterial material = createMaterial();
        LocalDate date = LocalDate.of(2026, 4, 10);
        MaterialDailyOverride override =
                new MaterialDailyOverride(material, date, 120);

        given(materialRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(material));
        given(dailyOverrideRepository
                .findByMaterialIdAndOverrideDate(1L, date))
                .willReturn(Optional.of(override));

        materialService.deleteDailyOverride(1L, 1L, date);

        verify(dailyOverrideRepository).delete(override);
    }

    // --- Review Config ---

    @Test
    @DisplayName("복습 설정을 저장한다 (신규)")
    void updateReviewConfig_new() {
        StudyMaterial material = createMaterial();
        given(materialRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(material));
        given(reviewConfigRepository.findByMaterialId(1L))
                .willReturn(Optional.empty());

        ReviewConfigResponse result =
                materialService.updateReviewConfig(1L, 1L,
                        new ReviewConfigRequest(
                                "1,3,7,14,30",
                                MissedPolicy.IMMEDIATE));

        verify(reviewConfigRepository)
                .save(any(ReviewConfig.class));
    }

    @Test
    @DisplayName("복습 설정을 수정한다 (기존)")
    void updateReviewConfig_existing() {
        StudyMaterial material = createMaterial();
        ReviewConfig existing = new ReviewConfig(
                material, "1,3,7", MissedPolicy.RESET);

        given(materialRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(material));
        given(reviewConfigRepository.findByMaterialId(1L))
                .willReturn(Optional.of(existing));

        ReviewConfigResponse result =
                materialService.updateReviewConfig(1L, 1L,
                        new ReviewConfigRequest(
                                "1,3,7,14,30",
                                MissedPolicy.IMMEDIATE));

        assertThat(result.intervals()).isEqualTo("1,3,7,14,30");
        assertThat(result.missedPolicy())
                .isEqualTo(MissedPolicy.IMMEDIATE);
    }
}
