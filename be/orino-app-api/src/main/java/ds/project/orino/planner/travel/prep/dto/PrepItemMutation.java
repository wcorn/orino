package ds.project.orino.planner.travel.prep.dto;

import ds.project.orino.domain.planner.travel.entity.PrepCategory;

/**
 * 항목 하나를 만들거나 고친 결과. <b>바뀐 항목과 갱신된 집계를 함께 내린다</b>(API §10).
 *
 * <p>체크 한 번에 진행률·사이드바 배지·상단 경고가 같이 움직이는데, 화면이 그것을 스스로
 * 다시 계산하면 서버와 어긋난다 — 기한 지남 판정은 첫날 기준 도시의 오늘을 알아야 하고
 * 그 시각은 브라우저에 없다.
 *
 * @param category 항목이 실제로 들어간 분류. 생략하고 만들면 서버가 {@code TODO}로 정하므로
 *                 화면은 이 값을 보고 어느 카드를 펼칠지 안다
 */
public record PrepItemMutation(
        PrepCategory category,
        PrepItemView item,
        PrepSummary summary
) {
}
