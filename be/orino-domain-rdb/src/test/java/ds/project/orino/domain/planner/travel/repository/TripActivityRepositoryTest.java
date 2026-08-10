package ds.project.orino.domain.planner.travel.repository;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.travel.entity.Trip;
import ds.project.orino.domain.planner.travel.entity.TripActivity;
import ds.project.orino.domain.support.RepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 일정 매핑과 보드 조회 규칙을 고정한다. 핵심은 두 가지 — 벽시계 시각이 변환 없이 왕복하는지,
 * 정렬이 시각이 아니라 {@code sortOrder}를 따르는지.
 *
 * <p>FK cascade(여행 삭제 → 일정 삭제)는 이 모듈이 {@code create-drop}으로 엔티티에서 스키마를
 * 만들어(FK 없음) 검증할 수 없다 — Liquibase 스키마의 몫이라 app-api 통합 테스트·로컬 확인에서 본다.
 */
@RepositoryTest
@Transactional
class TripActivityRepositoryTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 10, 24);
    private static final LocalDate DAY2 = LocalDate.of(2026, 10, 25);

    @Autowired
    private TripActivityRepository activityRepository;
    @Autowired
    private TripRepository tripRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Long tripId;

    @BeforeEach
    void setUp() {
        Long memberId = memberRepository.save(new Member("traveler", "pw")).getId();
        tripId = tripRepository.save(new Trip(memberId, "도쿄", DAY1,
                LocalDate.of(2026, 10, 27))).getId();
    }

    @Test
    @DisplayName("일정 필드가 그대로 저장·조회된다")
    void savesAndLoadsActivity() {
        TripActivity saved = activityRepository.save(
                new TripActivity(tripId, "센소지", DAY1, 0, LocalTime.of(10, 30)));
        saved.update("센소지", LocalTime.of(10, 30), "나카미세 거리부터", "https://example.com/senso-ji");
        activityRepository.flush();

        TripActivity found = activityRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getTripId()).isEqualTo(tripId);
        assertThat(found.getTitle()).isEqualTo("센소지");
        assertThat(found.getActivityDate()).isEqualTo(DAY1);
        assertThat(found.getSortOrder()).isZero();
        assertThat(found.getMemo()).isEqualTo("나카미세 거리부터");
        assertThat(found.getUrl()).isEqualTo("https://example.com/senso-ji");
        assertThat(found.getPlaceId()).isNull();
        assertThat(found.isNotifyEnabled()).isFalse();
        assertThat(found.getNotifyMinutes()).isNull();
        assertThat(found.isDepartureNotifyEnabled()).isFalse();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("시작 시각은 벽시계 값 그대로 왕복한다(UTC 변환 없음)")
    void startTimeRoundTripsAsWallClock() {
        // 서버·JDBC가 UTC로 돌아도 09:00은 09:00이어야 한다. 환산이 끼면 여기서 어긋난다.
        Long id = activityRepository.save(
                new TripActivity(tripId, "이른 출발", DAY1, 0, LocalTime.of(9, 0))).getId();

        assertThat(activityRepository.findById(id).orElseThrow().getStartTime())
                .isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    @DisplayName("자정 직후·직전 시각도 날짜를 넘기지 않는다")
    void keepsMidnightBoundaryTimes() {
        Long justAfter = activityRepository.save(
                new TripActivity(tripId, "심야 도착", DAY1, 0, LocalTime.of(0, 5))).getId();
        Long justBefore = activityRepository.save(
                new TripActivity(tripId, "막차", DAY1, 1, LocalTime.of(23, 55))).getId();

        assertThat(activityRepository.findById(justAfter).orElseThrow().getStartTime())
                .isEqualTo(LocalTime.of(0, 5));
        assertThat(activityRepository.findById(justAfter).orElseThrow().getActivityDate())
                .isEqualTo(DAY1);
        assertThat(activityRepository.findById(justBefore).orElseThrow().getStartTime())
                .isEqualTo(LocalTime.of(23, 55));
    }

    @Test
    @DisplayName("보드 정렬은 시각이 아니라 sort_order를 따른다")
    void ordersBySortOrderNotByTime() {
        // 시각으로 정렬하면 09:00이 앞서지만, 사용자가 드래그로 정한 순서가 우선이다.
        Long late = activityRepository.save(
                new TripActivity(tripId, "늦은 일정", DAY1, 0, LocalTime.of(18, 0))).getId();
        Long early = activityRepository.save(
                new TripActivity(tripId, "이른 일정", DAY1, 1, LocalTime.of(9, 0))).getId();

        assertThat(activityRepository
                .findAllByTripIdAndActivityDateOrderBySortOrderAscIdAsc(tripId, DAY1))
                .extracting(TripActivity::getId)
                .containsExactly(late, early);
    }

    @Test
    @DisplayName("시각 없는 일정도 순서대로 섞여 들어간다")
    void allowsActivitiesWithoutTime() {
        Long timed = activityRepository.save(
                new TripActivity(tripId, "조식", DAY1, 0, LocalTime.of(8, 0))).getId();
        Long untimed = activityRepository.save(
                new TripActivity(tripId, "동네 산책", DAY1, 1, null)).getId();

        List<TripActivity> day1 = activityRepository
                .findAllByTripIdAndActivityDateOrderBySortOrderAscIdAsc(tripId, DAY1);

        assertThat(day1).extracting(TripActivity::getId).containsExactly(timed, untimed);
        assertThat(day1.get(1).getStartTime()).isNull();
    }

    @Test
    @DisplayName("보드 조회는 보관함을 먼저, 이어서 날짜·순서대로 준다")
    void boardQueryPutsUnscheduledFirst() {
        Long day2 = activityRepository.save(
                new TripActivity(tripId, "디즈니씨", DAY2, 0, null)).getId();
        Long day1b = activityRepository.save(
                new TripActivity(tripId, "우에노", DAY1, 1, null)).getId();
        Long day1a = activityRepository.save(
                new TripActivity(tripId, "센소지", DAY1, 0, null)).getId();
        Long archived = activityRepository.save(
                new TripActivity(tripId, "가고 싶은 라멘집", null, 0, null)).getId();

        assertThat(activityRepository.findAllByTripIdOrderByActivityDateAscSortOrderAscIdAsc(tripId))
                .extracting(TripActivity::getId)
                .containsExactly(archived, day1a, day1b, day2);
    }

    @Test
    @DisplayName("보관함 조회는 날짜 없는 일정만 순서대로 준다")
    void findsUnscheduledOnly() {
        activityRepository.save(new TripActivity(tripId, "센소지", DAY1, 0, null));
        Long second = activityRepository.save(
                new TripActivity(tripId, "후보 B", null, 1, null)).getId();
        Long first = activityRepository.save(
                new TripActivity(tripId, "후보 A", null, 0, null)).getId();

        assertThat(activityRepository.findUnscheduled(tripId))
                .extracting(TripActivity::getId)
                .containsExactly(first, second);
    }

    @Test
    @DisplayName("보드·보관함 조회는 다른 여행의 일정을 섞지 않는다")
    void scopedByTrip() {
        Long otherMember = memberRepository.save(new Member("other", "pw")).getId();
        Long otherTrip = tripRepository.save(new Trip(otherMember, "오사카", DAY1,
                DAY2)).getId();
        activityRepository.save(new TripActivity(otherTrip, "남의 일정", DAY1, 0, null));
        activityRepository.save(new TripActivity(otherTrip, "남의 후보", null, 0, null));
        Long mine = activityRepository.save(new TripActivity(tripId, "센소지", DAY1, 0, null)).getId();

        assertThat(activityRepository.findAllByTripIdOrderByActivityDateAscSortOrderAscIdAsc(tripId))
                .extracting(TripActivity::getId)
                .containsExactly(mine);
        assertThat(activityRepository.findUnscheduled(tripId)).isEmpty();
        assertThat(activityRepository.countByTripId(tripId)).isEqualTo(1);
    }

    @Test
    @DisplayName("findByIdAndTripId는 다른 여행의 일정을 넘겨주지 않는다")
    void findByIdScopedByTrip() {
        Long otherMember = memberRepository.save(new Member("other", "pw")).getId();
        Long otherTrip = tripRepository.save(new Trip(otherMember, "오사카", DAY1,
                DAY2)).getId();
        Long id = activityRepository.save(new TripActivity(tripId, "센소지", DAY1, 0, null)).getId();

        assertThat(activityRepository.findByIdAndTripId(id, tripId)).isPresent();
        assertThat(activityRepository.findByIdAndTripId(id, otherTrip)).isEmpty();
    }

    @Test
    @DisplayName("nextSortOrder는 그 날짜의 맨 뒤 순서를 준다(빈 날짜면 0)")
    void nextSortOrderPerDate() {
        assertThat(activityRepository.nextSortOrder(tripId, DAY1)).isZero();

        activityRepository.save(new TripActivity(tripId, "센소지", DAY1, 0, null));
        activityRepository.save(new TripActivity(tripId, "우에노", DAY1, 1, null));
        // 다른 날짜는 자기 순서를 따로 센다.
        activityRepository.save(new TripActivity(tripId, "디즈니씨", DAY2, 0, null));

        assertThat(activityRepository.nextSortOrder(tripId, DAY1)).isEqualTo(2);
        assertThat(activityRepository.nextSortOrder(tripId, DAY2)).isEqualTo(1);
    }

    @Test
    @DisplayName("nextSortOrder는 날짜가 null이면 보관함 순서를 센다")
    void nextSortOrderForArchive() {
        activityRepository.save(new TripActivity(tripId, "센소지", DAY1, 0, null));
        assertThat(activityRepository.nextSortOrder(tripId, null)).isZero();

        activityRepository.save(new TripActivity(tripId, "후보 A", null, 0, null));

        assertThat(activityRepository.nextSortOrder(tripId, null)).isEqualTo(1);
    }

    @Test
    @DisplayName("기간이 줄면 밖으로 밀려난 일정만 골라낸다(보관함은 대상 아님)")
    void findsActivitiesOutsideShrunkPeriod() {
        Long before = activityRepository.save(
                new TripActivity(tripId, "출발 전날", LocalDate.of(2026, 10, 23), 0, null)).getId();
        Long after = activityRepository.save(
                new TripActivity(tripId, "마지막날", LocalDate.of(2026, 10, 27), 0, null)).getId();
        activityRepository.save(new TripActivity(tripId, "센소지", DAY1, 0, null));
        activityRepository.save(new TripActivity(tripId, "후보 라멘집", null, 0, null));

        // 기간을 10/24~10/26으로 줄였다.
        assertThat(activityRepository.findOutsidePeriod(tripId, DAY1, LocalDate.of(2026, 10, 26)))
                .extracting(TripActivity::getId)
                .containsExactly(before, after);
    }

    @Test
    @DisplayName("날짜 이동은 activity_date와 sort_order를 함께 바꾼다")
    void moveAcrossDates() {
        TripActivity activity = activityRepository.save(
                new TripActivity(tripId, "센소지", DAY1, 3, LocalTime.of(10, 0)));

        activity.moveTo(DAY2, 0);
        activityRepository.flush();

        TripActivity found = activityRepository.findById(activity.getId()).orElseThrow();
        assertThat(found.getActivityDate()).isEqualTo(DAY2);
        assertThat(found.getSortOrder()).isZero();
        assertThat(activityRepository
                .findAllByTripIdAndActivityDateOrderBySortOrderAscIdAsc(tripId, DAY1)).isEmpty();
    }

    @Test
    @DisplayName("보관함으로 내린 일정은 날짜 조회에서 빠지고 보관함 조회에 잡힌다")
    void moveToArchive() {
        TripActivity activity = activityRepository.save(
                new TripActivity(tripId, "센소지", DAY1, 0, LocalTime.of(10, 0)));

        activity.moveTo(null, 0);
        activityRepository.flush();

        assertThat(activityRepository
                .findAllByTripIdAndActivityDateOrderBySortOrderAscIdAsc(tripId, DAY1)).isEmpty();
        assertThat(activityRepository.findUnscheduled(tripId))
                .extracting(TripActivity::getId)
                .containsExactly(activity.getId());
    }

    @Test
    @DisplayName("재인덱싱하면 0..n-1 순서로 다시 읽힌다")
    void reindexesWithinDay() {
        TripActivity a = activityRepository.save(new TripActivity(tripId, "A", DAY1, 0, null));
        TripActivity b = activityRepository.save(new TripActivity(tripId, "B", DAY1, 1, null));
        TripActivity c = activityRepository.save(new TripActivity(tripId, "C", DAY1, 2, null));

        // C를 맨 앞으로 끌어올린 뒤 전체를 0..n-1로 재부여한다.
        List.of(c, a, b).forEach(activity ->
                activity.reorderTo(List.of(c, a, b).indexOf(activity)));
        activityRepository.flush();

        assertThat(activityRepository
                .findAllByTripIdAndActivityDateOrderBySortOrderAscIdAsc(tripId, DAY1))
                .extracting(TripActivity::getTitle)
                .containsExactly("C", "A", "B");
    }
}
