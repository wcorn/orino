package ds.project.orino.domain.planner.push.entity;

/**
 * 알림 상태.
 *
 * <p>{@code CANCELED}가 있는 이유 — 재계산 때 지우지 않고 전이시킨다. 알림이 왜 그 시각에
 * 갔는지, 혹은 왜 안 갔는지를 나중에 추적할 수 있어야 한다.
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    CANCELED
}
