package ds.project.orino.planner.review.controller;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.repository.FlashcardRepository;
import ds.project.orino.domain.planner.material.entity.MaterialType;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.support.ApiTestSupport;
import ds.project.orino.support.AuthFixture;
import ds.project.orino.support.DbCleaner;
import ds.project.orino.support.MemberFixture;
import ds.project.orino.support.PreRolloverClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 학습일 경계(04:00)를 <b>지나는</b> 시각에서의 오늘 복습.
 *
 * <p>다른 복습 테스트는 전부 KST 11:00에 고정돼 있어 이 창을 아예 안 지난다. 그래서 "앱의 오늘"과
 * "달력 오늘"이 하루 어긋나는 자정~04:00에서만 깨지는 결함이 6일간 잠복했다(#1055,
 * <a href="https://github.com/wcorn/orino/wiki/ELOG-025-study-day-test-helper-drift">ELOG-025</a>).
 *
 * <p>여기서는 Clock을 KST 01:00으로 못박아 그 창을 <b>매 빌드</b> 지나가게 한다 — 벽시계가
 * 몇 시든 상관없다.
 */
@Import(PreRolloverClockConfig.class)
class TodayReviewsRolloverTest extends ApiTestSupport {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private StudyMaterialRepository studyMaterialRepository;
    @Autowired
    private FlashcardRepository flashcardRepository;
    @Autowired
    private ReviewScheduleRepository reviewScheduleRepository;
    @Autowired
    private DbCleaner dbCleaner;
    @Autowired
    private Clock clock;

    private Member member;
    private Flashcard card;
    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        dbCleaner.clean();
        member = memberRepository.save(MemberFixture.create());
        StudyMaterial material = studyMaterialRepository.save(
                new StudyMaterial(member.getId(), "이펙티브 자바", MaterialType.BOOK));
        card = flashcardRepository.save(new Flashcard(member.getId(), material.getId(), "Q", "A"));
        authHeader = "Bearer " + AuthFixture.loginAndGetAccessToken(mockMvc);
    }

    private void scheduleAt(String studyDay) {
        reviewScheduleRepository.save(new ReviewSchedule(
                member.getId(), card.getId(), 1,
                atTestZone(LocalDate.parse(studyDay).atTime(4, 0)), 1, new BigDecimal("2.50")));
    }

    @Test
    @DisplayName("KST 01:00에는 달력 날짜와 학습일이 하루 다르다 — 이 어긋남이 결함의 무대였다")
    void calendarDayAndStudyDayDiffer() {
        assertThat(testToday(clock)).isEqualTo(LocalDate.parse(PreRolloverClockConfig.STUDY_DAY));
        assertThat(clock.instant().atZone(TEST_ZONE).toLocalDate())
                .isEqualTo(LocalDate.parse(PreRolloverClockConfig.CALENDAR_DAY));
    }

    @Test
    @DisplayName("응답의 today는 달력 날짜가 아니라 학습일이다")
    void todayIsStudyDay() throws Exception {
        mockMvc.perform(get("/api/planner/reviews/today")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.today").value(PreRolloverClockConfig.STUDY_DAY));
    }

    @Test
    @DisplayName("새벽 1시에도 그 학습일 복습이 오늘 목록에 있다 — 자정을 넘겼다고 사라지지 않는다")
    void includesReviewOfCurrentStudyDay() throws Exception {
        scheduleAt(PreRolloverClockConfig.STUDY_DAY);

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews", hasSize(1)))
                .andExpect(jsonPath("$.data.reviews[0].flashcard.id").value(card.getId()));
    }

    @Test
    @DisplayName("다음 학습일(04:00 이후) 복습은 아직 오늘이 아니다")
    void excludesNextStudyDay() throws Exception {
        scheduleAt(PreRolloverClockConfig.CALENDAR_DAY);

        mockMvc.perform(get("/api/planner/reviews/today")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews", hasSize(0)));
    }
}
