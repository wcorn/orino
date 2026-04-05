package ds.project.orino.batch.job.reschedule;

import ds.project.orino.batch.support.BatchIntegrationTest;
import ds.project.orino.domain.calendar.entity.DailySchedule;
import ds.project.orino.domain.calendar.repository.DailyScheduleRepository;
import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@BatchIntegrationTest
class ConsecutiveMissedDaysDetectorTest {

    @Autowired private ConsecutiveMissedDaysDetector detector;
    @Autowired private DailyScheduleRepository dailyScheduleRepository;
    @Autowired private MemberRepository memberRepository;

    private Member member;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        dailyScheduleRepository.deleteAll();
        memberRepository.deleteAll();
        member = memberRepository.save(new Member("batch-user", "pw"));
        today = LocalDate.now();
    }

    @Test
    @DisplayName("연속 3일 전부 성취율 50% 미만이면 미래 스케줄을 dirty 마킹한다")
    void detectsConsecutiveMissed() {
        saveSchedule(today.minusDays(3), 10, 2);
        saveSchedule(today.minusDays(2), 10, 3);
        saveSchedule(today.minusDays(1), 10, 4);
        DailySchedule future = saveSchedule(today.plusDays(1), 10, 0);

        int affected = detector.detectAndMarkDirty(today, 3, 50);

        assertThat(affected).isEqualTo(1);
        assertThat(reload(future).isDirty()).isTrue();
    }

    @Test
    @DisplayName("하루라도 성취율이 threshold 이상이면 dirty 마킹하지 않는다")
    void skipsWhenOneDayAboveThreshold() {
        saveSchedule(today.minusDays(3), 10, 2);
        saveSchedule(today.minusDays(2), 10, 8);
        saveSchedule(today.minusDays(1), 10, 3);
        DailySchedule future = saveSchedule(today.plusDays(1), 10, 0);

        int affected = detector.detectAndMarkDirty(today, 3, 50);

        assertThat(affected).isZero();
        assertThat(reload(future).isDirty()).isFalse();
    }

    @Test
    @DisplayName("과거 기록이 threshold일 미만이면 dirty 마킹하지 않는다")
    void skipsWhenInsufficientHistory() {
        saveSchedule(today.minusDays(2), 10, 1);
        saveSchedule(today.minusDays(1), 10, 1);
        DailySchedule future = saveSchedule(today.plusDays(1), 10, 0);

        int affected = detector.detectAndMarkDirty(today, 3, 50);

        assertThat(affected).isZero();
        assertThat(reload(future).isDirty()).isFalse();
    }

    private DailySchedule saveSchedule(LocalDate date,
                                       int total, int completed) {
        DailySchedule s = dailyScheduleRepository.save(
                new DailySchedule(member, date));
        s.markGenerated(total, completed);
        return dailyScheduleRepository.save(s);
    }

    private DailySchedule reload(DailySchedule s) {
        return dailyScheduleRepository.findById(s.getId()).orElseThrow();
    }
}
