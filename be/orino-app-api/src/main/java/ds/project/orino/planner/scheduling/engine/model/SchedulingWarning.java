package ds.project.orino.planner.scheduling.engine.model;

/**
 * 스케줄링 과정에서 발생한 경고.
 * 용량 초과, 데드라인 임박 이월 등을 포함한다.
 */
public record SchedulingWarning(
        WarningType type,
        String message,
        Long referenceId
) {
    public enum WarningType {
        /** 배치 못한 항목이 다음 날로 이월됨 (데드라인 경고) */
        DEADLINE_RISK,
        /** 자유시간 부족으로 일부 항목 이월 */
        CAPACITY_EXCEEDED,
        /** 학습 자료 시간 할당 초과 */
        ALLOCATION_EXCEEDED
    }
}
