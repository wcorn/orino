package ds.project.orino.planner.lifelog.moment.dto;

import java.util.List;

/**
 * 피드 한 페이지. {@code nextCursor}가 null이면 마지막 페이지다.
 */
public record FeedResponse(
        List<MomentCard> items,
        String nextCursor
) {
}
