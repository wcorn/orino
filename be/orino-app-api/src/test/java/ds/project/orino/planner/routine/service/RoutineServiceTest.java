package ds.project.orino.planner.routine.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.domain.fixedschedule.entity.RecurrenceType;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.routine.entity.Routine;
import ds.project.orino.domain.routine.entity.RoutineCheck;
import ds.project.orino.domain.routine.entity.RoutineException;
import ds.project.orino.domain.routine.entity.RoutineStatus;
import ds.project.orino.domain.routine.repository.RoutineCheckRepository;
import ds.project.orino.domain.routine.repository.RoutineExceptionRepository;
import ds.project.orino.domain.routine.repository.RoutineRepository;
import ds.project.orino.planner.routine.dto.CreateRoutineRequest;
import ds.project.orino.planner.routine.dto.RoutineCheckRequest;
import ds.project.orino.planner.routine.dto.RoutineCheckResponse;
import ds.project.orino.planner.routine.dto.RoutineExceptionRequest;
import ds.project.orino.planner.routine.dto.RoutineExceptionResponse;
import ds.project.orino.planner.routine.dto.RoutineResponse;
import ds.project.orino.planner.routine.dto.UpdateRoutineRequest;
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
class RoutineServiceTest {

    private RoutineService service;

    @Mock private RoutineRepository routineRepository;
    @Mock private RoutineCheckRepository routineCheckRepository;
    @Mock private RoutineExceptionRepository routineExceptionRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private DirtyScheduleMarker dirtyScheduleMarker;

    private Member member;

    @BeforeEach
    void setUp() {
        service = new RoutineService(routineRepository, routineCheckRepository,
                routineExceptionRepository, memberRepository, categoryRepository,
                dirtyScheduleMarker);
        member = new Member("admin", "encoded");
    }

    @Test
    @DisplayName("루틴 목록을 조회한다")
    void getRoutines() {
        Routine routine = new Routine(member, "운동", null, 30, null,
                RecurrenceType.DAILY, null, null,
                LocalDate.of(2026, 4, 1), null, false);

        given(routineRepository.findByMemberIdOrderByCreatedAtDesc(1L))
                .willReturn(List.of(routine));

        List<RoutineResponse> result = service.getRoutines(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("운동");
    }

    @Test
    @DisplayName("루틴을 생성한다")
    void create() {
        Routine saved = new Routine(member, "독서", null, 60, null,
                RecurrenceType.DAILY, null, null,
                LocalDate.of(2026, 4, 1), null, false);

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(routineRepository.save(any(Routine.class))).willReturn(saved);

        RoutineResponse result = service.create(1L,
                new CreateRoutineRequest("독서", null, 60, null,
                        RecurrenceType.DAILY, null, null,
                        LocalDate.of(2026, 4, 1), null, false));

        assertThat(result.title()).isEqualTo("독서");
        assertThat(result.durationMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("루틴을 수정한다")
    void update() {
        Routine routine = new Routine(member, "기존", null, 30, null,
                RecurrenceType.DAILY, null, null,
                LocalDate.of(2026, 4, 1), null, false);

        given(routineRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(routine));

        RoutineResponse result = service.update(1L, 1L,
                new UpdateRoutineRequest("수정됨", null, 45, null,
                        RecurrenceType.WEEKLY, null, "MON,WED,FRI",
                        LocalDate.of(2026, 4, 1), null, false));

        assertThat(result.title()).isEqualTo("수정됨");
        assertThat(result.durationMinutes()).isEqualTo(45);
    }

    @Test
    @DisplayName("루틴을 삭제한다")
    void delete() {
        Routine routine = new Routine(member, "삭제대상", null, 30, null,
                RecurrenceType.DAILY, null, null,
                LocalDate.of(2026, 4, 1), null, false);

        given(routineRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(routine));

        service.delete(1L, 1L);

        verify(routineRepository).delete(routine);
    }

    @Test
    @DisplayName("루틴 상태를 변경한다")
    void changeStatus() {
        Routine routine = new Routine(member, "루틴", null, 30, null,
                RecurrenceType.DAILY, null, null,
                LocalDate.of(2026, 4, 1), null, false);

        given(routineRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(routine));

        RoutineResponse result = service.changeStatus(1L, 1L,
                RoutineStatus.PAUSED);

        assertThat(result.status()).isEqualTo(RoutineStatus.PAUSED);
    }

    @Test
    @DisplayName("루틴을 체크한다")
    void check() {
        Routine routine = new Routine(member, "루틴", null, 30, null,
                RecurrenceType.DAILY, null, null,
                LocalDate.of(2026, 4, 1), null, false);
        RoutineCheck saved = new RoutineCheck(routine, LocalDate.of(2026, 4, 4));

        given(routineRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(routine));
        given(routineCheckRepository.save(any(RoutineCheck.class)))
                .willReturn(saved);

        RoutineCheckResponse result = service.check(1L, 1L,
                new RoutineCheckRequest(LocalDate.of(2026, 4, 4)));

        assertThat(result.completed()).isTrue();
    }

    @Test
    @DisplayName("루틴 체크를 취소한다")
    void uncheck() {
        Routine routine = new Routine(member, "루틴", null, 30, null,
                RecurrenceType.DAILY, null, null,
                LocalDate.of(2026, 4, 1), null, false);
        RoutineCheck check = new RoutineCheck(routine, LocalDate.of(2026, 4, 4));

        given(routineRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(routine));
        given(routineCheckRepository.findByRoutineIdAndCheckDate(
                1L, LocalDate.of(2026, 4, 4)))
                .willReturn(Optional.of(check));

        service.uncheckByDate(1L, 1L, LocalDate.of(2026, 4, 4));

        verify(routineCheckRepository).delete(check);
    }

    @Test
    @DisplayName("예외일을 추가한다")
    void addException() {
        Routine routine = new Routine(member, "루틴", null, 30, null,
                RecurrenceType.DAILY, null, null,
                LocalDate.of(2026, 4, 1), null, false);
        RoutineException saved = new RoutineException(
                routine, LocalDate.of(2026, 4, 10));

        given(routineRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(routine));
        given(routineExceptionRepository.save(any(RoutineException.class)))
                .willReturn(saved);

        RoutineExceptionResponse result = service.addException(1L, 1L,
                new RoutineExceptionRequest(LocalDate.of(2026, 4, 10)));

        assertThat(result.exceptionDate())
                .isEqualTo(LocalDate.of(2026, 4, 10));
    }

    @Test
    @DisplayName("예외일을 삭제한다")
    void removeException() {
        Routine routine = new Routine(member, "루틴", null, 30, null,
                RecurrenceType.DAILY, null, null,
                LocalDate.of(2026, 4, 1), null, false);
        RoutineException exception = new RoutineException(
                routine, LocalDate.of(2026, 4, 10));

        given(routineRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(routine));
        given(routineExceptionRepository.findByIdAndRoutineId(1L, 1L))
                .willReturn(Optional.of(exception));

        service.removeException(1L, 1L, 1L);

        verify(routineExceptionRepository).delete(exception);
    }

    @Test
    @DisplayName("존재하지 않는 루틴 조회 시 예외를 던진다")
    void getRoutine_notFound() {
        given(routineRepository.findByIdAndMemberId(999L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRoutine(1L, 999L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("존재하지 않는 루틴 삭제 시 예외를 던진다")
    void delete_notFound() {
        given(routineRepository.findByIdAndMemberId(999L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 999L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
