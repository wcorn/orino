package ds.project.orino.planner.routine.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.category.entity.Category;
import ds.project.orino.domain.category.repository.CategoryRepository;
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
import ds.project.orino.planner.routine.dto.RoutineDetailResponse;
import ds.project.orino.planner.routine.dto.RoutineExceptionRequest;
import ds.project.orino.planner.routine.dto.RoutineExceptionResponse;
import ds.project.orino.planner.routine.dto.RoutineResponse;
import ds.project.orino.planner.routine.dto.StreakInfo;
import ds.project.orino.planner.routine.dto.UpdateRoutineRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RoutineService {

    private final RoutineRepository routineRepository;
    private final RoutineCheckRepository routineCheckRepository;
    private final RoutineExceptionRepository routineExceptionRepository;
    private final MemberRepository memberRepository;
    private final CategoryRepository categoryRepository;

    public RoutineService(RoutineRepository routineRepository,
                          RoutineCheckRepository routineCheckRepository,
                          RoutineExceptionRepository routineExceptionRepository,
                          MemberRepository memberRepository,
                          CategoryRepository categoryRepository) {
        this.routineRepository = routineRepository;
        this.routineCheckRepository = routineCheckRepository;
        this.routineExceptionRepository = routineExceptionRepository;
        this.memberRepository = memberRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<RoutineResponse> getRoutines(Long memberId) {
        return routineRepository.findByMemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(r -> RoutineResponse.from(r, calculateStreak(r)))
                .toList();
    }

    public RoutineDetailResponse getRoutine(Long memberId, Long routineId) {
        Routine routine = routineRepository.findByIdAndMemberId(routineId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        return RoutineDetailResponse.from(routine, calculateStreak(routine));
    }

    @Transactional
    public RoutineResponse create(Long memberId, CreateRoutineRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        Category category = resolveCategory(request.categoryId(), memberId);

        Routine routine = new Routine(member, request.title(), category,
                request.durationMinutes(), request.preferredTime(),
                request.recurrenceType(), request.recurrenceInterval(),
                request.recurrenceDays(), request.startDate(),
                request.endDate(),
                Boolean.TRUE.equals(request.skipHolidays()));

        Routine saved = routineRepository.save(routine);
        return RoutineResponse.from(saved, new StreakInfo(0, 0));
    }

    @Transactional
    public RoutineResponse update(Long memberId, Long routineId,
                                  UpdateRoutineRequest request) {
        Routine routine = routineRepository.findByIdAndMemberId(routineId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        Category category = resolveCategory(request.categoryId(), memberId);

        routine.update(request.title(), category,
                request.durationMinutes(), request.preferredTime(),
                request.recurrenceType(), request.recurrenceInterval(),
                request.recurrenceDays(), request.startDate(),
                request.endDate(),
                Boolean.TRUE.equals(request.skipHolidays()));

        return RoutineResponse.from(routine, calculateStreak(routine));
    }

    @Transactional
    public void delete(Long memberId, Long routineId) {
        Routine routine = routineRepository.findByIdAndMemberId(routineId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        routineRepository.delete(routine);
    }

    @Transactional
    public RoutineResponse changeStatus(Long memberId, Long routineId,
                                        RoutineStatus status) {
        Routine routine = routineRepository.findByIdAndMemberId(routineId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        routine.changeStatus(status);
        return RoutineResponse.from(routine, calculateStreak(routine));
    }

    @Transactional
    public RoutineCheckResponse check(Long memberId, Long routineId,
                                      RoutineCheckRequest request) {
        Routine routine = routineRepository.findByIdAndMemberId(routineId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        RoutineCheck check = new RoutineCheck(routine, request.checkDate());
        return RoutineCheckResponse.from(routineCheckRepository.save(check));
    }

    @Transactional
    public void uncheckByDate(Long memberId, Long routineId,
                              LocalDate checkDate) {
        routineRepository.findByIdAndMemberId(routineId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        RoutineCheck check = routineCheckRepository
                .findByRoutineIdAndCheckDate(routineId, checkDate)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        routineCheckRepository.delete(check);
    }

    @Transactional
    public RoutineExceptionResponse addException(Long memberId, Long routineId,
                                                 RoutineExceptionRequest request) {
        Routine routine = routineRepository.findByIdAndMemberId(routineId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        RoutineException exception = new RoutineException(
                routine, request.exceptionDate());
        return RoutineExceptionResponse.from(
                routineExceptionRepository.save(exception));
    }

    @Transactional
    public void removeException(Long memberId, Long routineId,
                                Long exceptionId) {
        routineRepository.findByIdAndMemberId(routineId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        RoutineException exception = routineExceptionRepository
                .findByIdAndRoutineId(exceptionId, routineId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        routineExceptionRepository.delete(exception);
    }

    private StreakInfo calculateStreak(Routine routine) {
        List<LocalDate> checkedDates = routine.getChecks().stream()
                .filter(RoutineCheck::isCompleted)
                .map(RoutineCheck::getCheckDate)
                .sorted(Comparator.reverseOrder())
                .toList();

        if (checkedDates.isEmpty()) {
            return new StreakInfo(0, 0);
        }

        int currentStreak = 0;
        LocalDate expected = LocalDate.now();
        for (LocalDate date : checkedDates) {
            if (date.equals(expected) || date.equals(expected.minusDays(1))) {
                currentStreak++;
                expected = date.minusDays(1);
            } else if (currentStreak == 0 && date.equals(LocalDate.now().minusDays(1))) {
                currentStreak++;
                expected = date.minusDays(1);
            } else {
                break;
            }
        }

        int longestStreak = 0;
        int streak = 1;
        List<LocalDate> sorted = checkedDates.stream()
                .sorted()
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).equals(sorted.get(i - 1).plusDays(1))) {
                streak++;
            } else {
                longestStreak = Math.max(longestStreak, streak);
                streak = 1;
            }
        }
        longestStreak = Math.max(longestStreak, streak);

        return new StreakInfo(currentStreak, longestStreak);
    }

    private Category resolveCategory(Long categoryId, Long memberId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findByIdAndMemberId(categoryId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
