package ds.project.orino.planner.material.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.material.entity.MaterialStatus;
import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.unit.repository.StudyUnitRepository;
import ds.project.orino.planner.material.dto.MaterialCreateRequest;
import ds.project.orino.planner.material.dto.MaterialSummaryResponse;
import ds.project.orino.planner.material.dto.MaterialUpdateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StudyMaterialServiceTest {

    @Mock
    private StudyMaterialRepository studyMaterialRepository;

    @Mock
    private StudyUnitRepository studyUnitRepository;

    @Mock
    private ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository reviewScheduleRepository;

    @InjectMocks
    private StudyMaterialService studyMaterialService;

    @Test
    @DisplayName("create - 새 자료의 status는 ACTIVE이며 진행률은 0/0이다")
    void create_defaultStatusActive() {
        Long memberId = 1L;
        given(studyMaterialRepository.save(any(StudyMaterial.class)))
                .willAnswer(inv -> inv.getArgument(0));

        MaterialSummaryResponse response = studyMaterialService.create(
                memberId, new MaterialCreateRequest("이펙티브 자바", MaterialType.BOOK));

        assertThat(response.title()).isEqualTo("이펙티브 자바");
        assertThat(response.type()).isEqualTo(MaterialType.BOOK);
        assertThat(response.status()).isEqualTo(MaterialStatus.ACTIVE);
        assertThat(response.totalUnits()).isZero();
        assertThat(response.completedUnits()).isZero();
    }

    @Test
    @DisplayName("findOne - 본인 자료가 아니면 RESOURCE_NOT_FOUND를 던진다")
    void findOne_notOwned_throws() {
        given(studyMaterialRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> studyMaterialService.findOne(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("update - title만 제공되면 status는 변경하지 않는다")
    void update_partialTitleOnly() {
        Long memberId = 1L;
        Long materialId = 10L;
        StudyMaterial material = new StudyMaterial(memberId, "원본", MaterialType.BOOK);
        setId(material, materialId);
        given(studyMaterialRepository.findByIdAndMemberId(materialId, memberId))
                .willReturn(Optional.of(material));
        given(studyUnitRepository.countByMaterialIds(anyCollection()))
                .willReturn(List.of());

        MaterialSummaryResponse response = studyMaterialService.update(
                memberId, materialId, new MaterialUpdateRequest("새 제목", null));

        assertThat(response.title()).isEqualTo("새 제목");
        assertThat(response.status()).isEqualTo(MaterialStatus.ACTIVE);
        assertThat(material.getTitle()).isEqualTo("새 제목");
    }

    @Test
    @DisplayName("update - status만 제공되면 title은 변경하지 않는다")
    void update_partialStatusOnly() {
        Long memberId = 1L;
        Long materialId = 10L;
        StudyMaterial material = new StudyMaterial(memberId, "원본", MaterialType.BOOK);
        setId(material, materialId);
        given(studyMaterialRepository.findByIdAndMemberId(materialId, memberId))
                .willReturn(Optional.of(material));
        given(studyUnitRepository.countByMaterialIds(anyCollection()))
                .willReturn(List.of());

        MaterialSummaryResponse response = studyMaterialService.update(
                memberId, materialId, new MaterialUpdateRequest(null, MaterialStatus.COMPLETED));

        assertThat(response.title()).isEqualTo("원본");
        assertThat(response.status()).isEqualTo(MaterialStatus.COMPLETED);
        assertThat(material.getStatus()).isEqualTo(MaterialStatus.COMPLETED);
    }

    @Test
    @DisplayName("delete - 본인 자료가 아니면 삭제하지 않고 예외를 던진다")
    void delete_notOwned_doesNotDelete() {
        given(studyMaterialRepository.findByIdAndMemberId(10L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> studyMaterialService.delete(1L, 10L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(studyMaterialRepository, never()).delete(any());
    }

    private static void setId(StudyMaterial material, Long id) {
        try {
            java.lang.reflect.Field field = StudyMaterial.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(material, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
