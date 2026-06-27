package ds.project.orino.planner.holiday;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.holiday.dto.HolidayResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 공휴일 조회 엔드포인트. Google 연동과 무관하게 [from, to] 구간 공휴일을 반환한다.
 */
@RestController
@RequestMapping("/api/planner/holidays")
public class HolidayController {

    private final HolidayQueryService holidayQueryService;

    public HolidayController(HolidayQueryService holidayQueryService) {
        this.holidayQueryService = holidayQueryService;
    }

    @GetMapping
    public ApiResponse<List<HolidayResponse>> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(holidayQueryService.list(from, to));
    }
}
