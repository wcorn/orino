package ds.project.orino.planner.fixedschedule.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.domain.fixedschedule.entity.FixedSchedule;
import ds.project.orino.domain.fixedschedule.entity.RecurrenceType;
import ds.project.orino.domain.fixedschedule.repository.FixedScheduleRepository;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.planner.fixedschedule.dto.CreateFixedScheduleRequest;
import ds.project.orino.planner.fixedschedule.dto.FixedScheduleResponse;
import ds.project.orino.planner.fixedschedule.dto.UpdateFixedScheduleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FixedScheduleServiceTest {

    private FixedScheduleService service;

    @Mock
    private FixedScheduleRepository fixedScheduleRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private Member member;

    @BeforeEach
    void setUp() {
        service = new FixedScheduleService(
                fixedScheduleRepository, memberRepository, categoryRepository);
        member = new Member("admin", "encoded");
    }

    @Test
    @DisplayName("고정 일정 목록을 조회한다")
    void getFixedSchedules() {
        FixedSchedule schedule = new FixedSchedule(
                member, "수업", null,
                LocalTime.of(9, 0), LocalTime.of(10, 30),
                LocalDate.of(2026, 4, 10), RecurrenceType.NONE,
                null, null, null, null);

        given(fixedScheduleRepository.findByMemberIdOrderByStartTime(1L))
                .willReturn(List.of(schedule));

        List<FixedScheduleResponse> result = service.getFixedSchedules(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("수업");
    }

    @Test
    @DisplayName("단발성 고정 일정을 생성한다")
    void create_single() {
        FixedSchedule saved = new FixedSchedule(
                member, "면접", null,
                LocalTime.of(14, 0), LocalTime.of(15, 0),
                LocalDate.of(2026, 4, 15), RecurrenceType.NONE,
                null, null, null, null);

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(fixedScheduleRepository.save(any(FixedSchedule.class)))
                .willReturn(saved);

        FixedScheduleResponse result = service.create(1L,
                new CreateFixedScheduleRequest("면접", null,
                        LocalTime.of(14, 0), LocalTime.of(15, 0),
                        LocalDate.of(2026, 4, 15), RecurrenceType.NONE,
                        null, null, null, null));

        assertThat(result.title()).isEqualTo("면접");
        assertThat(result.recurrenceType()).isEqualTo(RecurrenceType.NONE);
    }

    @Test
    @DisplayName("주간 반복 고정 일정을 생성한다")
    void create_weekly() {
        FixedSchedule saved = new FixedSchedule(
                member, "운동", null,
                LocalTime.of(7, 0), LocalTime.of(8, 0),
                null, RecurrenceType.WEEKLY,
                null, "MON,WED,FRI",
                LocalDate.of(2026, 4, 1), null);

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(fixedScheduleRepository.save(any(FixedSchedule.class)))
                .willReturn(saved);

        FixedScheduleResponse result = service.create(1L,
                new CreateFixedScheduleRequest("운동", null,
                        LocalTime.of(7, 0), LocalTime.of(8, 0),
                        null, RecurrenceType.WEEKLY,
                        null, "MON,WED,FRI",
                        LocalDate.of(2026, 4, 1), null));

        assertThat(result.title()).isEqualTo("운동");
        assertThat(result.recurrenceDays()).isEqualTo("MON,WED,FRI");
    }

    @Test
    @DisplayName("NONE 타입에 scheduleDate 없으면 예외를 던진다")
    void create_noneWithoutDate() {
        assertThatThrownBy(() -> service.create(1L,
                new CreateFixedScheduleRequest("테스트", null,
                        LocalTime.of(9, 0), LocalTime.of(10, 0),
                        null, RecurrenceType.NONE,
                        null, null, null, null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("EVERY_N_DAYS에 interval 없으면 예외를 던진다")
    void create_everyNDaysWithoutInterval() {
        assertThatThrownBy(() -> service.create(1L,
                new CreateFixedScheduleRequest("테스트", null,
                        LocalTime.of(9, 0), LocalTime.of(10, 0),
                        null, RecurrenceType.EVERY_N_DAYS,
                        null, null, LocalDate.of(2026, 4, 1), null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("반복 타입에 recurrenceStart 없으면 예외를 던진다")
    void create_recurringWithoutStart() {
        assertThatThrownBy(() -> service.create(1L,
                new CreateFixedScheduleRequest("테스트", null,
                        LocalTime.of(9, 0), LocalTime.of(10, 0),
                        null, RecurrenceType.WEEKLY,
                        null, "MON", null, null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("고정 일정을 수정한다")
    void update() {
        FixedSchedule schedule = new FixedSchedule(
                member, "기존", null,
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                LocalDate.of(2026, 4, 10), RecurrenceType.NONE,
                null, null, null, null);

        given(fixedScheduleRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(schedule));

        FixedScheduleResponse result = service.update(1L, 1L,
                new UpdateFixedScheduleRequest("수정됨", null,
                        LocalTime.of(10, 0), LocalTime.of(11, 0),
                        LocalDate.of(2026, 4, 20), RecurrenceType.NONE,
                        null, null, null, null));

        assertThat(result.title()).isEqualTo("수정됨");
        assertThat(result.startTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    @DisplayName("존재하지 않는 고정 일정 수정 시 예외를 던진다")
    void update_notFound() {
        given(fixedScheduleRepository.findByIdAndMemberId(999L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, 999L,
                new UpdateFixedScheduleRequest("이름", null,
                        LocalTime.of(9, 0), LocalTime.of(10, 0),
                        LocalDate.of(2026, 4, 10), RecurrenceType.NONE,
                        null, null, null, null)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("고정 일정을 삭제한다")
    void delete() {
        FixedSchedule schedule = new FixedSchedule(
                member, "삭제대상", null,
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                LocalDate.of(2026, 4, 10), RecurrenceType.NONE,
                null, null, null, null);

        given(fixedScheduleRepository.findByIdAndMemberId(1L, 1L))
                .willReturn(Optional.of(schedule));

        service.delete(1L, 1L);

        verify(fixedScheduleRepository).delete(schedule);
    }

    @Test
    @DisplayName("존재하지 않는 고정 일정 삭제 시 예외를 던진다")
    void delete_notFound() {
        given(fixedScheduleRepository.findByIdAndMemberId(999L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 999L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
