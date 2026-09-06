package ds.project.orino.planner.travel.prep.dto;

import ds.project.orino.domain.planner.travel.entity.PrepCategory;

import java.util.List;

/**
 * 분류 하나. <b>항목이 하나도 없어도 내려간다</b> — 화면이 빈 분류 카드를 그려야 하고,
 * FE가 분류 목록을 따로 들고 있으면 서버와 두 벌이 되어 다섯 번째 분류가 조용히 생긴다.
 *
 * <p>항목은 <b>묶음을 거쳐서만</b> 실린다({@link PrepSection}). 묶음 밖에 항목 목록을 같이
 * 두지 않는다 — 두 벌이 되는 순간 어느 쪽이 진짜 순서인지가 매번 질문이 된다. 묶음을 하나도
 * 안 쓰는 분류는 {@code label}이 {@code null}인 묶음 하나로 내려가고, 화면은 그때 소제목을
 * 그리지 않는다(#1358).
 *
 * @param total 분류 전체 개수. 묶음별 개수는 각 {@link PrepSection}이 갖는다
 */
public record PrepGroup(
        PrepCategory category,
        int total,
        int done,
        List<PrepSection> sections
) {
}
