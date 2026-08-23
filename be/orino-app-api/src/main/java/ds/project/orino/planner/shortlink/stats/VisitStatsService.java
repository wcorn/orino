package ds.project.orino.planner.shortlink.stats;

import ds.project.orino.domain.planner.shortlink.entity.ShortlinkVisitDaily;
import ds.project.orino.domain.planner.shortlink.repository.ShortlinkVisitDailyRepository;
import ds.project.orino.domain.planner.shortlink.repository.ShortlinkVisitRepository;
import ds.project.orino.planner.shortlink.dto.LinkStatsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 방문 통계 조회. <b>저장하지 않고 매번 센다</b>(데이터 모델 §3) — 캐시 컬럼을 두면 갱신 주체가
 * 방문 경로와 정리 배치 둘이 되어 어긋난다.
 */
@Service
@Transactional(readOnly = true)
public class VisitStatsService {

    /** 집계 기준 시간대. 화면의 "오늘"과 같은 경계를 쓴다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int WEEK_DAYS = 7;

    private final ShortlinkVisitRepository visitRepository;
    private final ShortlinkVisitDailyRepository dailyRepository;
    private final Clock clock;

    public VisitStatsService(ShortlinkVisitRepository visitRepository,
                             ShortlinkVisitDailyRepository dailyRepository,
                             Clock clock) {
        this.visitRepository = visitRepository;
        this.dailyRepository = dailyRepository;
        this.clock = clock;
    }

    public LinkStatsResponse stats(Long shortlinkId, int rangeDays) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), KST);
        LocalDate from = today.minusDays(rangeDays - 1L);
        Instant rangeStart = from.atStartOfDay(KST).toInstant();

        ShortlinkVisitDailyRepository.VisitSumProjection sum =
                dailyRepository.sumByShortlinkId(shortlinkId);

        var deviceRows = visitRepository.countDevices(shortlinkId, rangeStart);
        long deviceTotal = deviceRows.stream().mapToLong(row -> row.getCount()).sum();
        List<LinkStatsResponse.DeviceRatio> devices = deviceRows.stream()
                .map(row -> new LinkStatsResponse.DeviceRatio(
                        row.getDevice(), ratio(row.getCount(), deviceTotal)))
                .toList();

        var countryRows = visitRepository.countCountries(shortlinkId, rangeStart);
        long countryTotal = countryRows.stream().mapToLong(row -> row.getCount()).sum();
        List<LinkStatsResponse.CountryRatio> countries = countryRows.stream()
                .map(row -> new LinkStatsResponse.CountryRatio(
                        row.getName(), ratio(row.getCount(), countryTotal)))
                .toList();

        return new LinkStatsResponse(
                sum.getVisits(),
                sum.getBots(),
                dailyRepository.sumVisitsSince(shortlinkId, today.minusDays(WEEK_DAYS - 1L)),
                visitRepository.findLastHumanVisitAt(shortlinkId),
                fillGaps(dailyRepository
                        .findAllByShortlinkIdAndVisitDateBetweenOrderByVisitDateAsc(
                                shortlinkId, from, today), from, today),
                visitRepository.countReferrers(shortlinkId, rangeStart).stream()
                        .map(row -> new LinkStatsResponse.ReferrerCount(row.getName(), row.getCount()))
                        .toList(),
                devices,
                countries);
    }

    /** 목록의 링크별 사람 방문 합계. */
    public Map<Long, Long> visitTotals(Collection<Long> shortlinkIds) {
        if (shortlinkIds.isEmpty()) {
            return Map.of();
        }
        return dailyRepository.sumByShortlinkIdIn(shortlinkIds).stream()
                .collect(Collectors.toMap(
                        ShortlinkVisitDailyRepository.VisitTotalProjection::getShortlinkId,
                        ShortlinkVisitDailyRepository.VisitTotalProjection::getTotal));
    }

    /** 목록의 링크별 마지막 사람 방문. 90일이 지난 링크는 값이 없다. */
    public Map<Long, Instant> lastVisits(Collection<Long> shortlinkIds) {
        if (shortlinkIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Instant> result = new HashMap<>();
        for (var row : visitRepository.findLastHumanVisitByShortlinkIdIn(shortlinkIds)) {
            result.put(row.getShortlinkId(), row.getLastVisitedAt());
        }
        return result;
    }

    public long visitTotal(Long shortlinkId) {
        return dailyRepository.sumByShortlinkId(shortlinkId).getVisits();
    }

    public Instant lastVisit(Long shortlinkId) {
        return visitRepository.findLastHumanVisitAt(shortlinkId);
    }

    /** {@code /select} 카드의 이번 주 방문 — 오늘 포함 7일. */
    public long visitsThisWeek(Long memberId) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), KST);
        return dailyRepository.sumVisitsByMemberSince(memberId, today.minusDays(WEEK_DAYS - 1L));
    }

    /**
     * 빈 날을 0으로 채운다. <b>화면이 날짜를 세지 않게</b> 서버가 범위 전체를 준다 —
     * 막대 그래프에서 빠진 날은 "방문 0"이 아니라 그래프의 구멍이 된다.
     */
    private List<LinkStatsResponse.DailyCount> fillGaps(List<ShortlinkVisitDaily> rows,
                                                        LocalDate from, LocalDate to) {
        Map<LocalDate, Long> counts = rows.stream().collect(Collectors.toMap(
                ShortlinkVisitDaily::getVisitDate, row -> (long) row.getVisitCount()));
        List<LinkStatsResponse.DailyCount> daily = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            daily.add(new LinkStatsResponse.DailyCount(date, counts.getOrDefault(date, 0L)));
        }
        return daily;
    }

    /**
     * 비율. <b>분모에서 UNKNOWN을 빼지 않는다</b> — 판정에 실패한 방문을 없는 것처럼 만들면
     * 합이 100%가 되면서 정확해 보이는 착시가 생긴다.
     *
     * <p>소수점 셋째 자리까지 준다. 화면은 퍼센트 정수로만 쓰지만, 반올림 자리를 화면마다
     * 다르게 정하지 않도록 서버가 한 번에 정해 둔다.
     */
    private double ratio(long count, long total) {
        return total == 0 ? 0 : Math.round(count * 1000.0 / total) / 1000.0;
    }
}
