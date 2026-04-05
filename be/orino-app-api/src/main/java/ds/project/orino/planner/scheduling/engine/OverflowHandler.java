package ds.project.orino.planner.scheduling.engine;

import ds.project.orino.planner.scheduling.engine.model.OverflowItem;
import ds.project.orino.planner.scheduling.engine.model.SchedulingWarning;
import ds.project.orino.planner.scheduling.engine.model.SchedulingWarning.WarningType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 자유시간에 배치하지 못한 항목을 처리한다.
 * 데드라인 경고 / 용량 초과 알림을 생성한다.
 *
 * 실제 이월(다음 날 재배치)은 dirty flag 방식이므로 별도 처리하지 않는다.
 * 다음 날 스케줄링 시 OVERDUE/미완료 항목으로 다시 수집된다.
 */
@Component
public class OverflowHandler {

    private static final Logger log =
            LoggerFactory.getLogger(OverflowHandler.class);

    public List<SchedulingWarning> handle(List<OverflowItem> overflows,
                                          LocalDate date) {
        List<SchedulingWarning> warnings = new ArrayList<>();
        for (OverflowItem overflow : overflows) {
            switch (overflow.reason()) {
                case SLOT_EXHAUSTED -> {
                    warnings.add(buildCapacityWarning(overflow, date));
                    if (hasDeadlineRisk(overflow, date)) {
                        warnings.add(buildDeadlineWarning(overflow, date));
                    }
                }
                case ALLOCATION_EXCEEDED -> warnings.add(
                        buildAllocationWarning(overflow));
                default -> { /* no-op */ }
            }
            log.info(
                    "overflow: item={} reason={} minutes={} due={}",
                    overflow.item().title(), overflow.reason(),
                    overflow.remainingMinutes(), overflow.item().due());
        }
        return warnings;
    }

    private boolean hasDeadlineRisk(OverflowItem overflow, LocalDate date) {
        LocalDate due = overflow.item().due();
        return due != null && !due.isBefore(date);
    }

    private SchedulingWarning buildCapacityWarning(OverflowItem overflow,
                                                   LocalDate date) {
        String msg = String.format(
                "'%s' 항목이 자유시간 부족으로 다음 날로 이월됩니다 (%d분 남음)",
                overflow.item().title(), overflow.remainingMinutes());
        return new SchedulingWarning(
                WarningType.CAPACITY_EXCEEDED,
                msg,
                overflow.item().referenceId());
    }

    private SchedulingWarning buildDeadlineWarning(OverflowItem overflow,
                                                   LocalDate date) {
        LocalDate due = overflow.item().due();
        long days = ChronoUnit.DAYS.between(date, due);
        String msg = String.format(
                "'%s' 데드라인까지 여유가 %d일 남았습니다",
                overflow.item().title(), days);
        return new SchedulingWarning(
                WarningType.DEADLINE_RISK,
                msg,
                overflow.item().referenceId());
    }

    private SchedulingWarning buildAllocationWarning(OverflowItem overflow) {
        String msg = String.format(
                "'%s' 항목이 학습 자료 시간 할당을 초과하여 %d분이 이월됩니다",
                overflow.item().title(), overflow.remainingMinutes());
        return new SchedulingWarning(
                WarningType.ALLOCATION_EXCEEDED,
                msg,
                overflow.item().referenceId());
    }
}
