package ds.project.orino.planner.material.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.category.entity.Category;
import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.domain.goal.entity.Goal;
import ds.project.orino.domain.goal.repository.GoalRepository;
import ds.project.orino.domain.material.entity.MaterialAllocation;
import ds.project.orino.domain.material.entity.MaterialDailyOverride;
import ds.project.orino.domain.material.entity.MaterialStatus;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MaterialService {

    private final StudyMaterialRepository materialRepository;
    private final StudyUnitRepository unitRepository;
    private final MaterialAllocationRepository allocationRepository;
    private final MaterialDailyOverrideRepository dailyOverrideRepository;
    private final ReviewConfigRepository reviewConfigRepository;
    private final MemberRepository memberRepository;
    private final CategoryRepository categoryRepository;
    private final GoalRepository goalRepository;
    private final DirtyScheduleMarker dirtyScheduleMarker;

    public MaterialService(
            StudyMaterialRepository materialRepository,
            StudyUnitRepository unitRepository,
            MaterialAllocationRepository allocationRepository,
            MaterialDailyOverrideRepository dailyOverrideRepository,
            ReviewConfigRepository reviewConfigRepository,
            MemberRepository memberRepository,
            CategoryRepository categoryRepository,
            GoalRepository goalRepository,
            DirtyScheduleMarker dirtyScheduleMarker) {
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.allocationRepository = allocationRepository;
        this.dailyOverrideRepository = dailyOverrideRepository;
        this.reviewConfigRepository = reviewConfigRepository;
        this.memberRepository = memberRepository;
        this.categoryRepository = categoryRepository;
        this.goalRepository = goalRepository;
        this.dirtyScheduleMarker = dirtyScheduleMarker;
    }

    public List<MaterialResponse> getMaterials(Long memberId) {
        return materialRepository
                .findByMemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(MaterialResponse::from)
                .toList();
    }

    public MaterialDetailResponse getMaterial(Long memberId,
                                              Long materialId) {
        StudyMaterial material = findMaterial(materialId, memberId);
        return MaterialDetailResponse.from(material);
    }

    @Transactional
    public MaterialResponse create(Long memberId,
                                   CreateMaterialRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));

        Category category = resolveCategory(
                request.categoryId(), memberId);
        Goal goal = resolveGoal(request.goalId(), memberId);

        StudyMaterial material = new StudyMaterial(
                member, request.title(), request.type(),
                category, goal,
                request.deadline(), request.deadlineMode());

        materialRepository.save(material);

        if (request.units() != null) {
            for (CreateUnitRequest unitReq : request.units()) {
                StudyUnit unit = new StudyUnit(
                        material, unitReq.title(),
                        unitReq.sortOrder(),
                        unitReq.estimatedMinutes(),
                        unitReq.difficulty());
                material.getUnits().add(unit);
            }
        }

        dirtyScheduleMarker.markDirtyFromToday(memberId);
        return MaterialResponse.from(material);
    }

    @Transactional
    public MaterialResponse update(Long memberId, Long materialId,
                                   UpdateMaterialRequest request) {
        StudyMaterial material = findMaterial(materialId, memberId);

        Category category = resolveCategory(
                request.categoryId(), memberId);
        Goal goal = resolveGoal(request.goalId(), memberId);

        material.update(request.title(), request.type(),
                category, goal,
                request.deadline(), request.deadlineMode());

        dirtyScheduleMarker.markDirtyFromToday(memberId);
        return MaterialResponse.from(material);
    }

    @Transactional
    public void delete(Long memberId, Long materialId) {
        StudyMaterial material = findMaterial(materialId, memberId);
        materialRepository.delete(material);
        dirtyScheduleMarker.markDirtyFromToday(memberId);
    }

    @Transactional
    public MaterialResponse pause(Long memberId, Long materialId) {
        StudyMaterial material = findMaterial(materialId, memberId);
        if (material.getStatus() != MaterialStatus.ACTIVE) {
            throw new CustomException(ErrorCode.INVALID_STATE);
        }
        material.pause();
        dirtyScheduleMarker.markDirtyFromToday(memberId);
        return MaterialResponse.from(material);
    }

    @Transactional
    public MaterialResponse resume(Long memberId, Long materialId) {
        StudyMaterial material = findMaterial(materialId, memberId);
        if (material.getStatus() != MaterialStatus.PAUSED) {
            throw new CustomException(ErrorCode.INVALID_STATE);
        }
        material.resume();
        dirtyScheduleMarker.markDirtyFromToday(memberId);
        return MaterialResponse.from(material);
    }

    // --- Study Unit ---

    @Transactional
    public UnitResponse createUnit(Long memberId, Long materialId,
                                   CreateUnitRequest request) {
        StudyMaterial material = findMaterial(materialId, memberId);
        StudyUnit unit = new StudyUnit(
                material, request.title(), request.sortOrder(),
                request.estimatedMinutes(), request.difficulty());
        UnitResponse response = UnitResponse.from(unitRepository.save(unit));
        dirtyScheduleMarker.markDirtyFromToday(memberId);
        return response;
    }

    @Transactional
    public List<UnitResponse> createUnitsBatch(
            Long memberId, Long materialId,
            CreateUnitBatchRequest request) {
        StudyMaterial material = findMaterial(materialId, memberId);
        List<UnitResponse> response = request.units().stream()
                .map(req -> {
                    StudyUnit unit = new StudyUnit(
                            material, req.title(), req.sortOrder(),
                            req.estimatedMinutes(), req.difficulty());
                    return UnitResponse.from(unitRepository.save(unit));
                })
                .toList();
        dirtyScheduleMarker.markDirtyFromToday(memberId);
        return response;
    }

    @Transactional
    public UnitResponse updateUnit(Long memberId, Long materialId,
                                   Long unitId,
                                   UpdateUnitRequest request) {
        findMaterial(materialId, memberId);
        StudyUnit unit = unitRepository
                .findByIdAndMaterialId(unitId, materialId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));
        unit.update(request.title(), request.sortOrder(),
                request.estimatedMinutes(), request.difficulty());
        dirtyScheduleMarker.markDirtyFromToday(memberId);
        return UnitResponse.from(unit);
    }

    @Transactional
    public void deleteUnit(Long memberId, Long materialId,
                           Long unitId) {
        findMaterial(materialId, memberId);
        StudyUnit unit = unitRepository
                .findByIdAndMaterialId(unitId, materialId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));
        unitRepository.delete(unit);
        dirtyScheduleMarker.markDirtyFromToday(memberId);
    }

    // --- Allocation ---

    @Transactional
    public AllocationResponse updateAllocation(
            Long memberId, Long materialId,
            AllocationRequest request) {
        StudyMaterial material = findMaterial(materialId, memberId);
        MaterialAllocation allocation = allocationRepository
                .findByMaterialId(materialId)
                .orElse(null);
        if (allocation == null) {
            allocation = new MaterialAllocation(
                    material, request.defaultMinutes());
            allocationRepository.save(allocation);
        } else {
            allocation.update(request.defaultMinutes());
        }
        dirtyScheduleMarker.markDirtyFromToday(memberId);
        return AllocationResponse.from(allocation);
    }

    // --- Daily Override ---

    public List<DailyOverrideResponse> getDailyOverrides(
            Long memberId, Long materialId) {
        findMaterial(materialId, memberId);
        return dailyOverrideRepository
                .findByMaterialIdOrderByOverrideDate(materialId)
                .stream()
                .map(DailyOverrideResponse::from)
                .toList();
    }

    @Transactional
    public DailyOverrideResponse updateDailyOverride(
            Long memberId, Long materialId, LocalDate date,
            DailyOverrideRequest request) {
        StudyMaterial material = findMaterial(materialId, memberId);
        MaterialDailyOverride override = dailyOverrideRepository
                .findByMaterialIdAndOverrideDate(materialId, date)
                .orElse(null);
        if (override == null) {
            override = new MaterialDailyOverride(
                    material, date, request.minutes());
            dailyOverrideRepository.save(override);
        } else {
            override.update(request.minutes());
        }
        dirtyScheduleMarker.markDirtyOn(memberId, date);
        return DailyOverrideResponse.from(override);
    }

    @Transactional
    public void deleteDailyOverride(Long memberId, Long materialId,
                                    LocalDate date) {
        findMaterial(materialId, memberId);
        MaterialDailyOverride override = dailyOverrideRepository
                .findByMaterialIdAndOverrideDate(materialId, date)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));
        dailyOverrideRepository.delete(override);
        dirtyScheduleMarker.markDirtyOn(memberId, date);
    }

    // --- Review Config ---

    @Transactional
    public ReviewConfigResponse updateReviewConfig(
            Long memberId, Long materialId,
            ReviewConfigRequest request) {
        StudyMaterial material = findMaterial(materialId, memberId);
        ReviewConfig config = reviewConfigRepository
                .findByMaterialId(materialId)
                .orElse(null);
        if (config == null) {
            config = new ReviewConfig(
                    material, request.intervals(),
                    request.missedPolicy());
            reviewConfigRepository.save(config);
        } else {
            config.update(request.intervals(),
                    request.missedPolicy());
        }
        dirtyScheduleMarker.markDirtyFromToday(memberId);
        return ReviewConfigResponse.from(config);
    }

    // --- Helpers ---

    private StudyMaterial findMaterial(Long materialId,
                                      Long memberId) {
        return materialRepository
                .findByIdAndMemberId(materialId, memberId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Category resolveCategory(Long categoryId,
                                     Long memberId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository
                .findByIdAndMemberId(categoryId, memberId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Goal resolveGoal(Long goalId, Long memberId) {
        if (goalId == null) {
            return null;
        }
        return goalRepository
                .findByIdAndMemberId(goalId, memberId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));
    }
}
