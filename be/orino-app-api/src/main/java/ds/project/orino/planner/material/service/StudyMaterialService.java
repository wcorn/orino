package ds.project.orino.planner.material.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.material.entity.MaterialStatus;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository.MaterialCountRow;
import ds.project.orino.planner.material.dto.MaterialCreateRequest;
import ds.project.orino.planner.material.dto.MaterialResponse;
import ds.project.orino.planner.material.dto.MaterialUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StudyMaterialService {

    private final StudyMaterialRepository studyMaterialRepository;
    private final Clock clock;

    public StudyMaterialService(StudyMaterialRepository studyMaterialRepository,
                                Clock clock) {
        this.studyMaterialRepository = studyMaterialRepository;
        this.clock = clock;
    }

    public List<MaterialResponse> findAll(Long memberId, MaterialStatus status) {
        List<StudyMaterial> materials = (status == null)
                ? studyMaterialRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId)
                : studyMaterialRepository.findAllByMemberIdAndStatusOrderByCreatedAtDesc(memberId, status);

        if (materials.isEmpty()) {
            return List.of();
        }
        return mapWithCounts(materials);
    }

    public MaterialResponse findOne(Long memberId, Long materialId) {
        StudyMaterial material = getOwnedMaterial(memberId, materialId);
        return mapWithCounts(List.of(material)).get(0);
    }

    @Transactional
    public MaterialResponse create(Long memberId, MaterialCreateRequest request) {
        StudyMaterial saved = studyMaterialRepository.save(
                new StudyMaterial(memberId, request.title(), request.type()));
        return MaterialResponse.of(saved, 0L, 0L);
    }

    @Transactional
    public MaterialResponse update(Long memberId, Long materialId, MaterialUpdateRequest request) {
        if (request.title() == null && request.status() == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
        StudyMaterial material = getOwnedMaterial(memberId, materialId);
        if (request.title() != null) {
            material.updateTitle(request.title());
        }
        if (request.status() != null) {
            material.updateStatus(request.status());
        }
        return mapWithCounts(List.of(material)).get(0);
    }

    @Transactional
    public void delete(Long memberId, Long materialId) {
        StudyMaterial material = getOwnedMaterial(memberId, materialId);
        studyMaterialRepository.delete(material);
    }

    private StudyMaterial getOwnedMaterial(Long memberId, Long materialId) {
        return studyMaterialRepository.findByIdAndMemberId(materialId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private List<MaterialResponse> mapWithCounts(List<StudyMaterial> materials) {
        List<Long> ids = materials.stream().map(StudyMaterial::getId).toList();
        Map<Long, Long> flashcardCounts = toCountMap(
                studyMaterialRepository.countFlashcardsByMaterialIds(ids));
        Map<Long, Long> dueReviewCounts = toCountMap(
                studyMaterialRepository.countDueReviewsByMaterialIds(ids, LocalDateTime.now(clock)));
        return materials.stream()
                .map(m -> MaterialResponse.of(m,
                        flashcardCounts.getOrDefault(m.getId(), 0L),
                        dueReviewCounts.getOrDefault(m.getId(), 0L)))
                .toList();
    }

    private static Map<Long, Long> toCountMap(List<MaterialCountRow> rows) {
        return rows.stream().collect(Collectors.toMap(
                MaterialCountRow::getMaterialId,
                MaterialCountRow::getCount,
                (a, b) -> a));
    }
}
