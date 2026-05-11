package ds.project.orino.planner.material.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.domain.planner.unit.entity.StudyUnit;
import ds.project.orino.domain.planner.unit.repository.StudyUnitRepository;
import ds.project.orino.planner.material.dto.UnitCreateRequest;
import ds.project.orino.planner.material.dto.UnitResponse;
import ds.project.orino.planner.material.dto.UnitUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class StudyUnitService {

    private final StudyMaterialRepository studyMaterialRepository;
    private final StudyUnitRepository studyUnitRepository;
    private final ReviewScheduleRepository reviewScheduleRepository;

    public StudyUnitService(StudyMaterialRepository studyMaterialRepository,
                            StudyUnitRepository studyUnitRepository,
                            ReviewScheduleRepository reviewScheduleRepository) {
        this.studyMaterialRepository = studyMaterialRepository;
        this.studyUnitRepository = studyUnitRepository;
        this.reviewScheduleRepository = reviewScheduleRepository;
    }

    @Transactional
    public List<UnitResponse> create(Long memberId, Long materialId, UnitCreateRequest request) {
        StudyMaterial material = studyMaterialRepository.findByIdAndMemberId(materialId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        int nextOrder = studyUnitRepository.findMaxSortOrderByMaterialId(material.getId()) + 1;

        List<StudyUnit> saved = new ArrayList<>(request.units().size());
        for (UnitCreateRequest.Item item : request.units()) {
            saved.add(studyUnitRepository.save(
                    new StudyUnit(memberId, material.getId(), item.title(), nextOrder++)));
        }
        return saved.stream().map(UnitResponse::from).toList();
    }

    @Transactional
    public UnitResponse update(Long memberId, Long unitId, UnitUpdateRequest request) {
        StudyUnit unit = studyUnitRepository.findByIdAndMemberId(unitId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        if (request.title() != null) {
            unit.updateTitle(request.title());
        }
        if (request.sortOrder() != null) {
            unit.updateSortOrder(request.sortOrder());
        }
        return UnitResponse.from(unit);
    }

    @Transactional
    public void delete(Long memberId, Long unitId) {
        StudyUnit unit = studyUnitRepository.findByIdAndMemberId(unitId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        reviewScheduleRepository.deleteAllByStudyUnitId(unit.getId());
        studyUnitRepository.delete(unit);
    }
}
