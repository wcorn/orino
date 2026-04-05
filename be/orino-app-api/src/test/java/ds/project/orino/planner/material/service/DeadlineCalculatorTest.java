package ds.project.orino.planner.material.service;

import ds.project.orino.domain.material.entity.DeadlineMode;
import ds.project.orino.domain.material.entity.MaterialAllocation;
import ds.project.orino.domain.material.entity.MaterialType;
import ds.project.orino.domain.material.entity.PaceStatus;
import ds.project.orino.domain.material.entity.StudyMaterial;
import ds.project.orino.domain.material.entity.StudyUnit;
import ds.project.orino.domain.material.entity.UnitStatus;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.preference.entity.UserPreference;
import ds.project.orino.planner.material.dto.DeadlineProjectionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DeadlineCalculatorTest {

    private DeadlineCalculator calculator;
    private Member member;

    @BeforeEach
    void setUp() {
        calculator = new DeadlineCalculator();
        member = new Member("user", "pw");
    }

    @Test
    @DisplayName("deadlineMode=FREE 이면 null을 반환한다")
    void returnsNull_whenFreeMode() {
        StudyMaterial material = new StudyMaterial(
                member, "자유모드", MaterialType.BOOK, null, null,
                null, DeadlineMode.FREE);
        addUnit(material, 1, 30, UnitStatus.PENDING);

        DeadlineProjectionResponse result = calculator.calculate(
                material, preference(null), LocalDate.of(2026, 4, 1));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("deadline이 null이면 null을 반환한다")
    void returnsNull_whenDeadlineNull() {
        StudyMaterial material = new StudyMaterial(
                member, "마감없음", MaterialType.BOOK, null, null,
                null, DeadlineMode.DEADLINE);

        DeadlineProjectionResponse result = calculator.calculate(
                material, preference(null), LocalDate.of(2026, 4, 1));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("남은 단위와 가용 학습일로 하루 필요량을 계산한다")
    void calculatesDailyRequirement() {
        // 2026-04-06(월) ~ 2026-04-12(일) = 7일, restDays 없음 → 7일
        StudyMaterial material = deadlineMaterial(LocalDate.of(2026, 4, 12));
        addUnit(material, 1, 30, UnitStatus.PENDING);
        addUnit(material, 2, 30, UnitStatus.PENDING);
        addUnit(material, 3, 30, UnitStatus.PENDING);
        addUnit(material, 4, 30, UnitStatus.PENDING);
        addUnit(material, 5, 30, UnitStatus.PENDING);
        addUnit(material, 6, 30, UnitStatus.PENDING);
        addUnit(material, 7, 30, UnitStatus.PENDING);
        setAllocation(material, 30);

        DeadlineProjectionResponse result = calculator.calculate(
                material, preference(null), LocalDate.of(2026, 4, 6));

        assertThat(result.availableDays()).isEqualTo(7);
        assertThat(result.remainingUnits()).isEqualTo(7);
        assertThat(result.remainingMinutes()).isEqualTo(210);
        assertThat(result.requiredUnitsPerDay()).isEqualTo(1);
        assertThat(result.requiredMinutesPerDay()).isEqualTo(30);
        assertThat(result.paceStatus()).isEqualTo(PaceStatus.ON_TRACK);
    }

    @Test
    @DisplayName("restDays에 포함된 요일은 가용 학습일에서 제외된다")
    void excludesRestDays() {
        // 2026-04-06(월) ~ 2026-04-12(일) = 7일
        // restDays=SAT,SUN → 5일만 가용
        StudyMaterial material = deadlineMaterial(LocalDate.of(2026, 4, 12));
        addUnit(material, 1, 30, UnitStatus.PENDING);

        DeadlineProjectionResponse result = calculator.calculate(
                material, preference("SAT,SUN"), LocalDate.of(2026, 4, 6));

        assertThat(result.availableDays()).isEqualTo(5);
    }

    @Test
    @DisplayName("완료된 단위는 remainingUnits 에서 제외된다")
    void excludesCompletedUnits() {
        StudyMaterial material = deadlineMaterial(LocalDate.of(2026, 4, 10));
        addUnit(material, 1, 30, UnitStatus.COMPLETED);
        addUnit(material, 2, 30, UnitStatus.COMPLETED);
        addUnit(material, 3, 60, UnitStatus.PENDING);

        DeadlineProjectionResponse result = calculator.calculate(
                material, preference(null), LocalDate.of(2026, 4, 6));

        assertThat(result.totalUnits()).isEqualTo(3);
        assertThat(result.completedUnits()).isEqualTo(2);
        assertThat(result.remainingUnits()).isEqualTo(1);
        assertThat(result.remainingMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("필요량이 할당량보다 적으면 AHEAD 상태다")
    void ahead_whenRequiredLessThanAllocated() {
        StudyMaterial material = deadlineMaterial(LocalDate.of(2026, 4, 12));
        addUnit(material, 1, 30, UnitStatus.PENDING);
        setAllocation(material, 120);

        DeadlineProjectionResponse result = calculator.calculate(
                material, preference(null), LocalDate.of(2026, 4, 6));

        assertThat(result.paceStatus()).isEqualTo(PaceStatus.AHEAD);
    }

    @Test
    @DisplayName("필요량이 할당량보다 많으면 BEHIND 상태다")
    void behind_whenRequiredExceedsAllocated() {
        StudyMaterial material = deadlineMaterial(LocalDate.of(2026, 4, 7));
        addUnit(material, 1, 120, UnitStatus.PENDING);
        addUnit(material, 2, 120, UnitStatus.PENDING);
        setAllocation(material, 60);

        DeadlineProjectionResponse result = calculator.calculate(
                material, preference(null), LocalDate.of(2026, 4, 6));

        assertThat(result.paceStatus()).isEqualTo(PaceStatus.BEHIND);
    }

    @Test
    @DisplayName("deadline이 이미 지났고 남은 단위가 있으면 BEHIND")
    void behind_whenDeadlinePassed() {
        StudyMaterial material = deadlineMaterial(LocalDate.of(2026, 4, 1));
        addUnit(material, 1, 30, UnitStatus.PENDING);
        setAllocation(material, 60);

        DeadlineProjectionResponse result = calculator.calculate(
                material, preference(null), LocalDate.of(2026, 4, 6));

        assertThat(result.availableDays()).isZero();
        assertThat(result.paceStatus()).isEqualTo(PaceStatus.BEHIND);
        assertThat(result.requiredMinutesPerDay()).isEqualTo(30);
    }

    @Test
    @DisplayName("남은 단위가 없으면 AHEAD")
    void ahead_whenNoRemaining() {
        StudyMaterial material = deadlineMaterial(LocalDate.of(2026, 4, 10));
        addUnit(material, 1, 30, UnitStatus.COMPLETED);
        addUnit(material, 2, 30, UnitStatus.COMPLETED);
        setAllocation(material, 60);

        DeadlineProjectionResponse result = calculator.calculate(
                material, preference(null), LocalDate.of(2026, 4, 6));

        assertThat(result.remainingUnits()).isZero();
        assertThat(result.paceStatus()).isEqualTo(PaceStatus.AHEAD);
    }

    @Test
    @DisplayName("allocation이 없으면 preference.dailyStudyMinutes 를 기준으로 판정")
    void fallsBackToPreferenceDailyStudyMinutes() {
        StudyMaterial material = deadlineMaterial(LocalDate.of(2026, 4, 10));
        addUnit(material, 1, 60, UnitStatus.PENDING);
        // allocation 설정 안 함

        UserPreference preference = preference(null);
        ReflectionTestUtils.setField(
                preference, "dailyStudyMinutes", 120);

        DeadlineProjectionResponse result = calculator.calculate(
                material, preference, LocalDate.of(2026, 4, 6));

        assertThat(result.allocatedMinutesPerDay()).isEqualTo(120);
        assertThat(result.paceStatus()).isEqualTo(PaceStatus.AHEAD);
    }

    // --- helpers ---

    private StudyMaterial deadlineMaterial(LocalDate deadline) {
        return new StudyMaterial(
                member, "교재", MaterialType.BOOK, null, null,
                deadline, DeadlineMode.DEADLINE);
    }

    private void addUnit(StudyMaterial material, int order,
                         int minutes, UnitStatus status) {
        StudyUnit unit = new StudyUnit(
                material, "단위" + order, order, minutes, null);
        if (status == UnitStatus.COMPLETED) {
            ReflectionTestUtils.setField(unit, "status",
                    UnitStatus.COMPLETED);
        }
        material.getUnits().add(unit);
    }

    private void setAllocation(StudyMaterial material, int minutes) {
        MaterialAllocation allocation =
                new MaterialAllocation(material, minutes);
        ReflectionTestUtils.setField(material, "allocation", allocation);
    }

    private UserPreference preference(String restDays) {
        UserPreference preference = new UserPreference(member);
        if (restDays != null) {
            ReflectionTestUtils.setField(preference, "restDays", restDays);
        }
        return preference;
    }
}
