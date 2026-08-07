package ds.project.orino.domain.planner.travel.entity;

import java.time.LocalDate;

/**
 * 여행 상태. <b>컬럼으로 저장하지 않고 매 조회 시 파생한다.</b>
 *
 * <p>저장하면 날짜가 넘어갈 때 상태를 갱신할 주체가 없어 반드시 어긋난다(예정인 채로 멈춘
 * 진행 중 여행). 기준 날짜는 기기 시간대가 아니라 <b>여행 타임존의 오늘</b>이다 —
 * {@link Trip#statusOn(LocalDate)}를 쓰면 이 기준이 강제된다.
 */
public enum TripStatus {

    /** 오늘 &lt; 시작일. */
    UPCOMING,

    /** 시작일 ≤ 오늘 ≤ 종료일. */
    ONGOING,

    /** 오늘 &gt; 종료일. */
    COMPLETED;

    /**
     * 기간과 기준 날짜를 비교해 상태를 판정한다.
     *
     * @param today     여행 타임존 기준 오늘 날짜
     * @param startDate 여행 시작일
     * @param endDate   여행 종료일(당일 포함)
     */
    public static TripStatus of(LocalDate today, LocalDate startDate, LocalDate endDate) {
        if (today.isBefore(startDate)) {
            return UPCOMING;
        }
        if (today.isAfter(endDate)) {
            return COMPLETED;
        }
        return ONGOING;
    }
}
