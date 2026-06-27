package ds.project.orino.planner.review.dto;

/**
 * 복습 미러 토글 결과. {@code reviewCalendarId}는 보조 캘린더("orino 복습") ID로, OFF여도 보존되어 빠른 재-enable에 쓰인다.
 */
public record ReviewMirrorStatusResponse(
        boolean enabled,
        String reviewCalendarId
) {
}
