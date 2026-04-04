package ds.project.orino.planner.preference.service;

import ds.project.orino.domain.material.entity.MissedPolicy;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.preference.entity.PriorityItemType;
import ds.project.orino.domain.preference.entity.PriorityRule;
import ds.project.orino.domain.preference.entity.StudyTimePreference;
import ds.project.orino.domain.preference.entity.UserPreference;
import ds.project.orino.domain.preference.repository.PriorityRuleRepository;
import ds.project.orino.domain.preference.repository.UserPreferenceRepository;
import ds.project.orino.planner.preference.dto.PreferenceResponse;
import ds.project.orino.planner.preference.dto.PriorityRuleRequest;
import ds.project.orino.planner.preference.dto.PriorityRuleResponse;
import ds.project.orino.planner.preference.dto.UpdatePreferenceRequest;
import ds.project.orino.planner.preference.dto.UpdatePriorityRulesRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PreferenceServiceTest {

    private PreferenceService preferenceService;

    @Mock private UserPreferenceRepository preferenceRepository;
    @Mock private PriorityRuleRepository ruleRepository;
    @Mock private MemberRepository memberRepository;

    private Member member;

    @BeforeEach
    void setUp() {
        preferenceService = new PreferenceService(
                preferenceRepository, ruleRepository,
                memberRepository);
        member = new Member("admin", "encoded");
    }

    @Test
    @DisplayName("설정을 조회한다 (기존)")
    void getPreference_existing() {
        UserPreference pref = new UserPreference(member);
        given(preferenceRepository.findByMemberId(1L))
                .willReturn(Optional.of(pref));

        PreferenceResponse result =
                preferenceService.getPreference(1L);

        assertThat(result.wakeTime())
                .isEqualTo(LocalTime.of(7, 0));
        assertThat(result.focusMinutes()).isEqualTo(50);
    }

    @Test
    @DisplayName("설정이 없으면 기본값을 생성한다")
    void getPreference_createDefault() {
        given(preferenceRepository.findByMemberId(1L))
                .willReturn(Optional.empty());
        given(memberRepository.findById(1L))
                .willReturn(Optional.of(member));
        given(preferenceRepository.save(
                any(UserPreference.class)))
                .willAnswer(inv -> inv.getArgument(0));

        PreferenceResponse result =
                preferenceService.getPreference(1L);

        verify(preferenceRepository)
                .save(any(UserPreference.class));
        assertThat(result.dailyStudyMinutes()).isEqualTo(240);
    }

    @Test
    @DisplayName("설정을 수정한다")
    void updatePreference() {
        UserPreference pref = new UserPreference(member);
        given(preferenceRepository.findByMemberId(1L))
                .willReturn(Optional.of(pref));

        PreferenceResponse result =
                preferenceService.updatePreference(1L,
                        new UpdatePreferenceRequest(
                                LocalTime.of(8, 0),
                                LocalTime.of(23, 0),
                                300,
                                StudyTimePreference.EVENING,
                                45, 15,
                                "SUN",
                                true,
                                "1,3,7,14",
                                MissedPolicy.SKIP,
                                3));

        assertThat(result.wakeTime())
                .isEqualTo(LocalTime.of(8, 0));
        assertThat(result.dailyStudyMinutes()).isEqualTo(300);
        assertThat(result.studyTimePreference())
                .isEqualTo(StudyTimePreference.EVENING);
        assertThat(result.streakFreezePerMonth()).isEqualTo(3);
    }

    @Test
    @DisplayName("우선순위 규칙을 조회한다")
    void getPriorityRules() {
        List<PriorityRule> rules = List.of(
                new PriorityRule(
                        member, PriorityItemType.DEADLINE, 0),
                new PriorityRule(
                        member, PriorityItemType.REVIEW, 1));

        given(ruleRepository
                .findByMemberIdOrderBySortOrder(1L))
                .willReturn(rules);

        List<PriorityRuleResponse> result =
                preferenceService.getPriorityRules(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).itemType())
                .isEqualTo(PriorityItemType.DEADLINE);
    }

    @Test
    @DisplayName("우선순위 규칙이 없으면 기본값을 생성한다")
    void getPriorityRules_createDefaults() {
        given(ruleRepository
                .findByMemberIdOrderBySortOrder(1L))
                .willReturn(List.of())
                .willReturn(List.of(
                        new PriorityRule(
                                member,
                                PriorityItemType.DEADLINE, 0)));

        given(memberRepository.findById(1L))
                .willReturn(Optional.of(member));
        given(ruleRepository.save(any(PriorityRule.class)))
                .willAnswer(inv -> inv.getArgument(0));

        List<PriorityRuleResponse> result =
                preferenceService.getPriorityRules(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("우선순위 규칙을 수정한다")
    void updatePriorityRules() {
        PriorityRule existing = new PriorityRule(
                member, PriorityItemType.DEADLINE, 0);

        given(memberRepository.findById(1L))
                .willReturn(Optional.of(member));
        given(ruleRepository.findByMemberIdAndItemType(
                1L, PriorityItemType.DEADLINE))
                .willReturn(Optional.of(existing));
        given(ruleRepository.findByMemberIdAndItemType(
                1L, PriorityItemType.REVIEW))
                .willReturn(Optional.empty());
        given(ruleRepository.save(any(PriorityRule.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(ruleRepository
                .findByMemberIdOrderBySortOrder(1L))
                .willReturn(List.of(existing));

        List<PriorityRuleResponse> result =
                preferenceService.updatePriorityRules(1L,
                        new UpdatePriorityRulesRequest(List.of(
                                new PriorityRuleRequest(
                                        PriorityItemType.DEADLINE,
                                        1),
                                new PriorityRuleRequest(
                                        PriorityItemType.REVIEW,
                                        0))));

        assertThat(existing.getSortOrder()).isEqualTo(1);
    }
}
