package ds.project.orino.planner.shortlink.dto;

import java.time.Instant;

/**
 * 목적지 교체 이력 한 줄. 첫 줄이 현재 목적지, 마지막 줄이 최초 발급이다.
 */
public record TargetHistoryEntry(String targetUrl, String reason, Instant changedAt) {
}
