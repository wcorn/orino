package ds.project.orino.planner.google.calendar.dto;

/** 통합 피드의 소스별 실패 정보. (예: source="google-events") */
public record FeedError(String source, String message) {
}
