package ds.project.orino.planner.material.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.material.entity.MaterialStatus;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.domain.planner.unit.entity.StudyUnit;
import ds.project.orino.domain.planner.unit.repository.StudyUnitRepository;
import ds.project.orino.planner.material.dto.MaterialCreateRequest;
import ds.project.orino.planner.material.dto.MaterialDetailResponse;
import ds.project.orino.planner.material.dto.MaterialSummaryResponse;
import ds.project.orino.planner.material.dto.MaterialUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StudyMaterialService {

    private final StudyMaterialRepository studyMaterialRepository;
    private final StudyUnitRepository studyUnitRepository;
    private final ReviewScheduleRepository reviewScheduleRepository;

    public StudyMaterialService(StudyMaterialRepository studyMaterialRepository,
                                StudyUnitRepository studyUnitRepository,
                                ReviewScheduleRepository reviewScheduleRepository) {
        this.studyMaterialRepository = studyMaterialRepository;
        this.studyUnitRepository = studyUnitRepository;
        this.reviewScheduleRepository = reviewScheduleRepository;
    }

    public List<MaterialSummaryResponse> findAll(Long memberId, MaterialStatus status) {
        List<StudyMaterial> materials = (status == null)
                ? studyMaterialRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId)
                : studyMaterialRepository.findAllByMemberIdAndStatusOrderByCreatedAtDesc(memberId, status);

        if (materials.isEmpty()) {
            return List.of();
        }

        List<Long> materialIds = materials.stream().map(StudyMaterial::getId).toList();
        Map<Long, StudyUnitRepository.UnitCountProjection> countByMaterial = studyUnitRepository
                .countByMaterialIds(materialIds).stream()
                .collect(Collectors.toMap(
                        StudyUnitRepository.UnitCountProjection::getMaterialId,
                        Function.identity()));

        return materials.stream()
                .map(m -> {
                    StudyUnitRepository.UnitCountProjection counts = countByMaterial.get(m.getId());
                    long total = counts == null ? 0L : counts.getTotalUnits();
                    long completed = counts == null ? 0L : counts.getCompletedUnits();
                    return MaterialSummaryResponse.of(m, total, completed);
                })
                .toList();
    }

    public MaterialDetailResponse findOne(Long memberId, Long materialId) {
        StudyMaterial material = getOwnedMaterial(memberId, materialId);
        List<StudyUnit> units = studyUnitRepository.findAllByMaterialIdOrderBySortOrderAsc(material.getId());
        return MaterialDetailResponse.of(material, units);
    }

    @Transactional
    public MaterialSummaryResponse create(Long memberId, MaterialCreateRequest request) {
        StudyMaterial saved = studyMaterialRepository.save(
                new StudyMaterial(memberId, request.title(), request.type()));
        return MaterialSummaryResponse.of(saved, 0L, 0L);
    }

    @Transactional
    public MaterialSummaryResponse update(Long memberId, Long materialId, MaterialUpdateRequest request) {
        StudyMaterial material = getOwnedMaterial(memberId, materialId);

        if (request.title() != null) {
            material.updateTitle(request.title());
        }
        if (request.status() != null) {
            material.updateStatus(request.status());
        }

        StudyUnitRepository.UnitCountProjection counts = studyUnitRepository
                .countByMaterialIds(List.of(material.getId())).stream()
                .findFirst()
                .orElse(null);
        long total = counts == null ? 0L : counts.getTotalUnits();
        long completed = counts == null ? 0L : counts.getCompletedUnits();
        return MaterialSummaryResponse.of(material, total, completed);
    }

    @Transactional
    public void delete(Long memberId, Long materialId) {
        StudyMaterial material = getOwnedMaterial(memberId, materialId);
        List<Long> unitIds = studyUnitRepository.findIdsByMaterialId(material.getId());
        if (!unitIds.isEmpty()) {
            reviewScheduleRepository.deleteAllByStudyUnitIdIn(unitIds);
        }
        studyUnitRepository.deleteAllByMaterialId(material.getId());
        studyMaterialRepository.delete(material);
    }

    private StudyMaterial getOwnedMaterial(Long memberId, Long materialId) {
        return studyMaterialRepository.findByIdAndMemberId(materialId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
