package ds.project.orino.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 학습일(study day) — 하루의 경계를 자정이 아니라 <b>새벽 {@value #ROLLOVER_HOUR}시</b>로 본다.
 *
 * <p>새벽 1시에 공부한 건 사용자에겐 "어젯밤 공부"다. 자정을 경계로 삼으면 그게 다음 날 몫이 되어
 * 첫 복습이 하루 밀리고, 실제로는 30시간 넘게 지나서야 다시 보게 된다(#1003). 복습 due 시각은
 * 원래부터 04:00 롤오버였으니, "지금이 며칠인가"도 같은 경계를 쓴다.
 *
 * <p>04:00 정각인 값은 경계 이동의 영향을 받지 않는다({@code D 04:00 − 4h = D 00:00} → D).
 * 이미 04:00으로 잡혀 있는 {@code scheduled_at}은 전부 예전과 같은 날짜로 매핑되므로,
 * 경계를 바꿔도 쌓인 데이터를 손댈 필요가 없다.
 */
public final class StudyDay {

    /** 하루가 바뀌는 시각(사용자 시간대 기준). 이 시각부터 새 학습일이다. */
    public static final int ROLLOVER_HOUR = 4;

    private StudyDay() {
    }

    /** 그 시각이 속한 학습일. 자정~04:00은 전날로 친다. */
    public static LocalDate of(Instant instant, ZoneId zone) {
        return instant.atZone(zone).minusHours(ROLLOVER_HOUR).toLocalDate();
    }

    /** 그 학습일이 시작하는 시각(= 그 날짜 04:00). */
    public static Instant startOf(LocalDate studyDay, ZoneId zone) {
        return studyDay.atTime(ROLLOVER_HOUR, 0).atZone(zone).toInstant();
    }
}
