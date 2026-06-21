package ds.project.orino.planner.holiday;

import ds.project.orino.domain.planner.holiday.entity.Holiday;
import ds.project.orino.domain.planner.holiday.repository.HolidayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.time.ZoneId;
import java.util.List;

/**
 * 특일정보 API로 공휴일을 가져와 {@code holiday} 테이블에 멱등 upsert한다.
 * 같은 날짜가 있으면 이름만 갱신(대체공휴일 명칭 변경 등), 없으면 추가한다.
 */
@Service
public class HolidaySyncService {

    private static final Logger log = LoggerFactory.getLogger(HolidaySyncService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final HolidayApiClient apiClient;
    private final HolidayRepository repository;
    private final HolidayProperties properties;

    public HolidaySyncService(HolidayApiClient apiClient,
                              HolidayRepository repository,
                              HolidayProperties properties) {
        this.apiClient = apiClient;
        this.repository = repository;
        this.properties = properties;
    }

    /** 올해부터 syncYears개년을 동기화한다. 인증키 미설정이면 건너뛴다. */
    public void syncUpcomingYears() {
        if (properties.serviceKey() == null || properties.serviceKey().isBlank()) {
            log.info("holiday sync skipped: service key not configured");
            return;
        }
        int start = Year.now(KST).getValue();
        int years = Math.max(1, properties.syncYears());
        for (int year = start; year < start + years; year++) {
            int count = sync(year);
            log.info("holiday sync {}: {} days", year, count);
        }
    }

    /** 한 해를 동기화하고 upsert한 건수를 반환한다. */
    @Transactional
    public int sync(int year) {
        List<HolidayApiClient.HolidayItem> items = apiClient.fetchYear(year);
        for (HolidayApiClient.HolidayItem item : items) {
            repository.findByDate(item.date())
                    .ifPresentOrElse(
                            existing -> existing.updateName(item.name()),
                            () -> repository.save(new Holiday(item.date(), item.name())));
        }
        return items.size();
    }
}
