package ds.project.orino.planner.review.dto;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 앞으로의 복습 항목이 언제 due인지. 시각 라벨은 FE가 포맷하고, 서버는 이 대분류만 제공한다.
 *
 * <ul>
 *     <li>{@link #NOW} — due 또는 임박한 재복습(scheduled_at ≤ now)</li>
 *     <li>{@link #TODAY} — 오늘 남음(scheduled_at &gt; now, 같은 날짜)</li>
 *     <li>{@link #FUTURE} — 내일 이후</li>
 * </ul>
 */
public enum WhenKind {
    NOW,
    TODAY,
    FUTURE;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
