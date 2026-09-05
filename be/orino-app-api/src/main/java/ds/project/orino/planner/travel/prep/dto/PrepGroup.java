package ds.project.orino.planner.travel.prep.dto;

import ds.project.orino.domain.planner.travel.entity.PrepCategory;

import java.util.List;

/**
 * 분류 하나. <b>항목이 하나도 없어도 내려간다</b> — 화면이 빈 분류 카드를 그려야 하고,
 * FE가 분류 목록을 따로 들고 있으면 서버와 두 벌이 되어 다섯 번째 분류가 조용히 생긴다.
 */
public record PrepGroup(
        PrepCategory category,
        int total,
        int done,
        List<PrepItemView> items
) {
}
