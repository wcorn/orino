package ds.project.orino.planner.reflection.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.reflection.entity.DailyReflection;
import ds.project.orino.domain.reflection.repository.DailyReflectionRepository;
import ds.project.orino.planner.reflection.dto.CreateReflectionRequest;
import ds.project.orino.planner.reflection.dto.ReflectionResponse;
import ds.project.orino.planner.reflection.dto.UpdateReflectionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional(readOnly = true)
public class ReflectionService {

    private final DailyReflectionRepository reflectionRepository;
    private final MemberRepository memberRepository;

    public ReflectionService(
            DailyReflectionRepository reflectionRepository,
            MemberRepository memberRepository) {
        this.reflectionRepository = reflectionRepository;
        this.memberRepository = memberRepository;
    }

    public ReflectionResponse getByDate(Long memberId, LocalDate date) {
        DailyReflection reflection = reflectionRepository
                .findByMemberIdAndReflectionDate(memberId, date)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));
        return ReflectionResponse.from(reflection);
    }

    @Transactional
    public ReflectionResponse create(
            Long memberId, CreateReflectionRequest request) {
        reflectionRepository
                .findByMemberIdAndReflectionDate(memberId, request.date())
                .ifPresent(existing -> {
                    throw new CustomException(ErrorCode.INVALID_STATE);
                });
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));
        DailyReflection reflection = reflectionRepository.save(
                new DailyReflection(member, request.date(),
                        request.mood(), request.memo()));
        return ReflectionResponse.from(reflection);
    }

    @Transactional
    public ReflectionResponse update(
            Long memberId, Long id, UpdateReflectionRequest request) {
        DailyReflection reflection = reflectionRepository.findById(id)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));
        if (!reflection.getMember().getId().equals(memberId)) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        reflection.update(request.mood(), request.memo());
        return ReflectionResponse.from(reflection);
    }
}
