package ds.project.orino.planner.scheduling.engine.model;

/**
 * 자유시간에 배치되지 못한 항목.
 */
public record OverflowItem(
        SchedulableItem item,
        int remainingMinutes,
        OverflowReason reason
) {
    public enum OverflowReason {
        /** 자유시간 전부 소진 */
        SLOT_EXHAUSTED,
        /** 시간 할당(하루 총 학습시간 등) 초과 */
        ALLOCATION_EXCEEDED
    }
}
