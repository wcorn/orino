package ds.project.orino.planner.material.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.unit.entity.StudyUnit;
import ds.project.orino.domain.planner.unit.entity.UnitStatus;
import ds.project.orino.domain.planner.unit.repository.StudyUnitRepository;
import ds.project.orino.planner.material.dto.UnitCreateRequest;
import ds.project.orino.planner.material.dto.UnitResponse;
import ds.project.orino.planner.material.dto.UnitUpdateRequest;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StudyUnitServiceTest {

    @Mock
    private StudyMaterialRepository studyMaterialRepository;

    @Mock
    private StudyUnitRepository studyUnitRepository;

    @Mock
    private ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository reviewScheduleRepository;

    @InjectMocks
    private StudyUnitService studyUnitService;

    @Test
    @DisplayName("create - 기존 단위가 없으면 sort_order는 1부터 시작한다")
    void create_firstUnit_sortOrderStartsAt1() {
        Long memberId = 1L;
        Long materialId = 10L;
        StudyMaterial material = new StudyMaterial(memberId, "자료", MaterialType.BOOK);
        setId(material, materialId);
        given(studyMaterialRepository.findByIdAndMemberId(materialId, memberId))
                .willReturn(Optional.of(material));
        given(studyUnitRepository.findMaxSortOrderByMaterialId(materialId)).willReturn(0);
        given(studyUnitRepository.save(any(StudyUnit.class)))
                .willAnswer(inv -> inv.getArgument(0));

        List<UnitResponse> result = studyUnitService.create(memberId, materialId,
                new UnitCreateRequest(List.of(new UnitCreateRequest.Item("u1"))));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sortOrder()).isEqualTo(1);
        assertThat(result.get(0).status()).isEqualTo(UnitStatus.PENDING);
    }

    @Test
    @DisplayName("create - 기존 단위가 있으면 max+1부터 순차 부여한다")
    void create_continuesFromMax() {
        Long memberId = 1L;
        Long materialId = 10L;
        StudyMaterial material = new StudyMaterial(memberId, "자료", MaterialType.BOOK);
        setId(material, materialId);
        given(studyMaterialRepository.findByIdAndMemberId(materialId, memberId))
                .willReturn(Optional.of(material));
        given(studyUnitRepository.findMaxSortOrderByMaterialId(materialId)).willReturn(5);
        given(studyUnitRepository.save(any(StudyUnit.class)))
                .willAnswer(inv -> inv.getArgument(0));

        List<UnitResponse> result = studyUnitService.create(memberId, materialId,
                new UnitCreateRequest(List.of(
                        new UnitCreateRequest.Item("u1"),
                        new UnitCreateRequest.Item("u2"))));

        assertThat(result).extracting(UnitResponse::sortOrder).containsExactly(6, 7);
    }

    @Test
    @DisplayName("create - 다른 멤버 자료면 RESOURCE_NOT_FOUND")
    void create_notOwnedMaterial_throws() {
        given(studyMaterialRepository.findByIdAndMemberId(10L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> studyUnitService.create(1L, 10L,
                new UnitCreateRequest(List.of(new UnitCreateRequest.Item("u")))))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(studyUnitRepository, never()).save(any());
    }

    @Test
    @DisplayName("update - 본인 단위가 아니면 RESOURCE_NOT_FOUND")
    void update_notOwned_throws() {
        given(studyUnitRepository.findByIdAndMemberId(10L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> studyUnitService.update(1L, 10L,
                new UnitUpdateRequest("새 제목", null)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("update - title만 제공하면 sortOrder는 그대로")
    void update_titleOnly() {
        StudyUnit unit = new StudyUnit(1L, 10L, "원본", 3);
        given(studyUnitRepository.findByIdAndMemberId(100L, 1L)).willReturn(Optional.of(unit));

        UnitResponse response = studyUnitService.update(1L, 100L,
                new UnitUpdateRequest("새 제목", null));

        assertThat(response.title()).isEqualTo("새 제목");
        assertThat(response.sortOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("delete - 본인 단위가 아니면 RESOURCE_NOT_FOUND")
    void delete_notOwned_throws() {
        given(studyUnitRepository.findByIdAndMemberId(10L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> studyUnitService.delete(1L, 10L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(studyUnitRepository, never()).delete(any());
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
