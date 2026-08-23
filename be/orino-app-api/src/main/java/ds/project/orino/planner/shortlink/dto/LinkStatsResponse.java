package ds.project.orino.planner.shortlink.dto;

import ds.project.orino.domain.planner.shortlink.entity.VisitDevice;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 방문 통계(API 설계 §2). <b>통계는 참고치다</b> — 정확도를 위해 프라이버시를 팔지 않는다.
 *
 * <p>{@code daily}는 집계 테이블에서 오므로 범위 제한이 없다. 반면
 * <b>{@code referrers}·{@code devices}·{@code countries}는 원시에서만 나오므로 90일 창을
 * 넘어가면 비어 간다</b>(명세 §8.3). 화면 기본이 30일이라 평소엔 보이지 않는 경계다.
 *
 * @param totalVisits   사람 방문 총계(집계 테이블, 전 기간)
 * @param botVisits     봇·프리뷰 총계. <b>따로 센다</b> — 같이 세면 실제의 몇 배가 된다
 * @param lastVisitedAt 마지막 사람 방문. 90일이 지나 원시가 지워지면 null이 된다
 * @param daily         요청 범위 전체. <b>빈 날도 0으로 채워서</b> 준다 — 화면이 날짜를 세지 않게
 */
public record LinkStatsResponse(
        long totalVisits,
        long botVisits,
        long last7Days,
        Instant lastVisitedAt,
        List<DailyCount> daily,
        List<ReferrerCount> referrers,
        List<DeviceRatio> devices,
        List<CountryRatio> countries
) {

    public record DailyCount(LocalDate date, long count) {
    }

    /** 도메인까지만. 전체 URL은 저장하지 않으므로 더 잘게 나눌 수 없다. */
    public record ReferrerCount(String domain, long count) {
    }

    public record DeviceRatio(VisitDevice device, double ratio) {
    }

    public record CountryRatio(String country, double ratio) {
    }
}
