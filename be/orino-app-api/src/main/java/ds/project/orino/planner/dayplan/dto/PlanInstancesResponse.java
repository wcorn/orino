package ds.project.orino.planner.dayplan.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 플랜 펼침(배경 레이어용). 날짜별로 해당일에 발생하는 모든 활성 플랜의 블록을 담는다.
 * 블록 없는 날짜는 응답에서 생략한다(omission).
 */
public record PlanInstancesResponse(
        List<Day> days
) {

    public record Day(
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate date,
            List<Block> blocks
    ) {
    }

    public record Block(
            Long planId,
            String planName,
            String color,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime endTime,
            String label
    ) {
    }
}
