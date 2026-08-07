package ds.project.orino.domain.planner.travel.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 알림 유효성은 DB 제약이 아니라 엔티티가 판정한다. 스위치만 보고 알림을 만들면 시각 없는
 * 일정·보관함 일정에 대해 "언제 보낼지 없는" 알림이 예약된다.
 */
class TripActivityTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 24);

    @Test
    @DisplayName("날짜와 시각이 모두 있고 스위치가 켜져야 알림 대상이다")
    void notifiableOnlyWithDateAndTime() {
        TripActivity scheduled = new TripActivity(1L, "센소지", DAY, 0, LocalTime.of(10, 0));
        scheduled.updateNotification(true, null, false);
        assertThat(scheduled.isNotifiable()).isTrue();
    }

    @Test
    @DisplayName("시각이 없으면 스위치가 켜져 있어도 알림을 만들지 않는다")
    void notNotifiableWithoutTime() {
        TripActivity noTime = new TripActivity(1L, "쇼핑", DAY, 0, null);
        noTime.updateNotification(true, 30, false);

        assertThat(noTime.isNotifiable()).isFalse();
    }

    @Test
    @DisplayName("보관함 일정(날짜 없음)은 알림 대상이 아니다")
    void notNotifiableWhenUnscheduled() {
        TripActivity archived = new TripActivity(1L, "후보", null, 0, LocalTime.of(10, 0));
        archived.updateNotification(true, null, false);

        assertThat(archived.isUnscheduled()).isTrue();
        assertThat(archived.isNotifiable()).isFalse();
    }

    @Test
    @DisplayName("스위치가 꺼져 있으면 날짜·시각이 있어도 대상이 아니다")
    void notNotifiableWhenDisabled() {
        TripActivity off = new TripActivity(1L, "센소지", DAY, 0, LocalTime.of(10, 0));

        assertThat(off.isNotifiable()).isFalse();
    }

    @Test
    @DisplayName("알림 시점은 자체 값 우선, 없으면 여행 기본값으로 떨어진다")
    void resolvesNotifyMinutes() {
        TripActivity own = new TripActivity(1L, "센소지", DAY, 0, LocalTime.of(10, 0));
        own.updateNotification(true, 30, false);
        assertThat(own.resolveNotifyMinutes(15)).isEqualTo(30);

        TripActivity inherited = new TripActivity(1L, "우에노", DAY, 1, LocalTime.of(13, 0));
        inherited.updateNotification(true, null, false);
        assertThat(inherited.resolveNotifyMinutes(15)).isEqualTo(15);
    }

    @Test
    @DisplayName("moveTo(null, …)은 일정을 보관함으로 내린다")
    void moveToArchive() {
        TripActivity activity = new TripActivity(1L, "센소지", DAY, 3, LocalTime.of(10, 0));

        activity.moveTo(null, 0);

        assertThat(activity.isUnscheduled()).isTrue();
        assertThat(activity.getSortOrder()).isZero();
        // 시각은 남는다 — 날짜만 정하면 그대로 되살아난다.
        assertThat(activity.getStartTime()).isEqualTo(LocalTime.of(10, 0));
    }
}
