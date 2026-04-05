package ds.project.orino.planner.preference.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.preference.entity.PriorityItemType;
import ds.project.orino.domain.preference.entity.PriorityRule;
import ds.project.orino.domain.preference.entity.UserPreference;
import ds.project.orino.domain.preference.repository.PriorityRuleRepository;
import ds.project.orino.domain.preference.repository.UserPreferenceRepository;
import ds.project.orino.planner.preference.dto.PreferenceResponse;
import ds.project.orino.planner.preference.dto.PriorityRuleRequest;
import ds.project.orino.planner.preference.dto.PriorityRuleResponse;
import ds.project.orino.planner.preference.dto.UpdatePreferenceRequest;
import ds.project.orino.planner.preference.dto.UpdatePriorityRulesRequest;
import ds.project.orino.planner.scheduling.dirty.DirtyScheduleMarker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PreferenceService {

    private final UserPreferenceRepository preferenceRepository;
    private final PriorityRuleRepository ruleRepository;
    private final MemberRepository memberRepository;
    private final DirtyScheduleMarker dirtyScheduleMarker;

    public PreferenceService(
            UserPreferenceRepository preferenceRepository,
            PriorityRuleRepository ruleRepository,
            MemberRepository memberRepository,
            DirtyScheduleMarker dirtyScheduleMarker) {
        this.preferenceRepository = preferenceRepository;
        this.ruleRepository = ruleRepository;
        this.memberRepository = memberRepository;
        this.dirtyScheduleMarker = dirtyScheduleMarker;
    }

    @Transactional
    public PreferenceResponse getPreference(Long memberId) {
        UserPreference pref = preferenceRepository
                .findByMemberId(memberId)
                .orElseGet(() -> createDefault(memberId));
        return PreferenceResponse.from(pref);
    }

    @Transactional
    public PreferenceResponse updatePreference(
            Long memberId, UpdatePreferenceRequest request) {
        UserPreference pref = preferenceRepository
                .findByMemberId(memberId)
                .orElseGet(() -> createDefault(memberId));

        pref.update(
                request.wakeTime(), request.sleepTime(),
                request.dailyStudyMinutes(),
                request.studyTimePreference(),
                request.focusMinutes(), request.breakMinutes(),
                request.restDays(), request.skipHolidays(),
                request.defaultReviewIntervals(),
                request.defaultMissedPolicy(),
                request.streakFreezePerMonth());

        dirtyScheduleMarker.markDirtyFromToday(memberId);
        return PreferenceResponse.from(pref);
    }

    @Transactional
    public List<PriorityRuleResponse> getPriorityRules(
            Long memberId) {
        List<PriorityRule> rules = ruleRepository
                .findByMemberIdOrderBySortOrder(memberId);
        if (rules.isEmpty()) {
            rules = createDefaultRules(memberId);
        }
        return rules.stream()
                .map(PriorityRuleResponse::from)
                .toList();
    }

    @Transactional
    public List<PriorityRuleResponse> updatePriorityRules(
            Long memberId,
            UpdatePriorityRulesRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));

        for (PriorityRuleRequest ruleReq : request.rules()) {
            PriorityRule rule = ruleRepository
                    .findByMemberIdAndItemType(
                            memberId, ruleReq.itemType())
                    .orElseGet(() -> {
                        PriorityRule newRule = new PriorityRule(
                                member, ruleReq.itemType(),
                                ruleReq.sortOrder());
                        return ruleRepository.save(newRule);
                    });
            rule.updateSortOrder(ruleReq.sortOrder());
        }

        List<PriorityRuleResponse> response = ruleRepository
                .findByMemberIdOrderBySortOrder(memberId)
                .stream()
                .map(PriorityRuleResponse::from)
                .toList();
        dirtyScheduleMarker.markDirtyFromToday(memberId);
        return response;
    }

    private UserPreference createDefault(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));
        return preferenceRepository.save(
                new UserPreference(member));
    }

    private List<PriorityRule> createDefaultRules(
            Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.RESOURCE_NOT_FOUND));

        PriorityItemType[] types = PriorityItemType.values();
        for (int i = 0; i < types.length; i++) {
            ruleRepository.save(
                    new PriorityRule(member, types[i], i));
        }
        return ruleRepository
                .findByMemberIdOrderBySortOrder(memberId);
    }
}
