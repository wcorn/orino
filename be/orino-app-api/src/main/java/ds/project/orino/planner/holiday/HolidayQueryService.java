package ds.project.orino.planner.holiday;

import ds.project.orino.domain.planner.holiday.repository.HolidayRepository;
import ds.project.orino.planner.holiday.dto.HolidayResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** 공휴일 조회. 통합 캘린더 FE 오버레이가 [from, to] 구간으로 가져간다. */
@Service
public class HolidayQueryService {

    private final HolidayRepository repository;

    public HolidayQueryService(HolidayRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<HolidayResponse> list(LocalDate from, LocalDate to) {
        return repository.findByDateBetween(from, to).stream()
                .map(h -> new HolidayResponse(h.getDate().toString(), h.getName()))
                .toList();
    }
}
