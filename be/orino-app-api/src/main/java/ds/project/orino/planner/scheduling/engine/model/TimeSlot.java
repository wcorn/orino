package ds.project.orino.planner.scheduling.engine.model;

import java.time.Duration;
import java.time.LocalTime;

/**
 * 자유 시간 블록을 표현한다. start는 포함, end는 제외한다.
 */
public record TimeSlot(LocalTime start, LocalTime end) {

    public TimeSlot {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start/end는 null일 수 없다");
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException(
                    "start는 end보다 이전이어야 한다: "
                            + start + " / " + end);
        }
    }

    public int minutes() {
        return (int) Duration.between(start, end).toMinutes();
    }

    public boolean contains(LocalTime time) {
        return !time.isBefore(start) && time.isBefore(end);
    }

    public boolean overlaps(TimeSlot other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public TimeSlot withStart(LocalTime newStart) {
        return new TimeSlot(newStart, end);
    }

    public TimeSlot withEnd(LocalTime newEnd) {
        return new TimeSlot(start, newEnd);
    }
}
