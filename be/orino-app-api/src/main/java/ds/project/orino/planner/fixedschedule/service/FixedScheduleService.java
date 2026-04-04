package ds.project.orino.planner.fixedschedule.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.category.entity.Category;
import ds.project.orino.domain.category.repository.CategoryRepository;
import ds.project.orino.domain.fixedschedule.entity.FixedSchedule;
import ds.project.orino.domain.fixedschedule.entity.RecurrenceType;
import ds.project.orino.domain.fixedschedule.repository.FixedScheduleRepository;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.planner.fixedschedule.dto.CreateFixedScheduleRequest;
import ds.project.orino.planner.fixedschedule.dto.FixedScheduleResponse;
import ds.project.orino.planner.fixedschedule.dto.UpdateFixedScheduleRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class FixedScheduleService {

    private final FixedScheduleRepository fixedScheduleRepository;
    private final MemberRepository memberRepository;
    private final CategoryRepository categoryRepository;

    public FixedScheduleService(FixedScheduleRepository fixedScheduleRepository,
                                MemberRepository memberRepository,
                                CategoryRepository categoryRepository) {
        this.fixedScheduleRepository = fixedScheduleRepository;
        this.memberRepository = memberRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<FixedScheduleResponse> getFixedSchedules(Long memberId) {
        return fixedScheduleRepository.findByMemberIdOrderByStartTime(memberId)
                .stream()
                .map(FixedScheduleResponse::from)
                .toList();
    }

    @Transactional
    public FixedScheduleResponse create(Long memberId,
                                        CreateFixedScheduleRequest request) {
        validateRecurrence(request.recurrenceType(), request.scheduleDate(),
                request.recurrenceInterval(), request.recurrenceStart());

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        Category category = resolveCategory(request.categoryId(), memberId);

        FixedSchedule schedule = new FixedSchedule(
                member, request.title(), category,
                request.startTime(), request.endTime(),
                request.scheduleDate(), request.recurrenceType(),
                request.recurrenceInterval(), request.recurrenceDays(),
                request.recurrenceStart(), request.recurrenceEnd());

        return FixedScheduleResponse.from(fixedScheduleRepository.save(schedule));
    }

    @Transactional
    public FixedScheduleResponse update(Long memberId, Long scheduleId,
                                        UpdateFixedScheduleRequest request) {
        validateRecurrence(request.recurrenceType(), request.scheduleDate(),
                request.recurrenceInterval(), request.recurrenceStart());

        FixedSchedule schedule = fixedScheduleRepository
                .findByIdAndMemberId(scheduleId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        Category category = resolveCategory(request.categoryId(), memberId);

        schedule.update(request.title(), category,
                request.startTime(), request.endTime(),
                request.scheduleDate(), request.recurrenceType(),
                request.recurrenceInterval(), request.recurrenceDays(),
                request.recurrenceStart(), request.recurrenceEnd());

        return FixedScheduleResponse.from(schedule);
    }

    @Transactional
    public void delete(Long memberId, Long scheduleId) {
        FixedSchedule schedule = fixedScheduleRepository
                .findByIdAndMemberId(scheduleId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        fixedScheduleRepository.delete(schedule);
    }

    private void validateRecurrence(RecurrenceType type,
                                    java.time.LocalDate scheduleDate,
                                    Integer interval,
                                    java.time.LocalDate recurrenceStart) {
        if (type == RecurrenceType.NONE && scheduleDate == null) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        if (type == RecurrenceType.EVERY_N_DAYS && (interval == null || interval < 1)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        if (type != RecurrenceType.NONE && recurrenceStart == null) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }

    private Category resolveCategory(Long categoryId, Long memberId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findByIdAndMemberId(categoryId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
