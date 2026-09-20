package ds.project.orino.planner.travel.prep.dto;

import ds.project.orino.domain.planner.travel.entity.PrepCategory;

import java.util.List;

/**
 * 분류 하나. <b>항목이 하나도 없어도 내려간다</b> — 화면이 빈 분류 카드를 그려야 하고,
 * FE가 분류 목록을 따로 들고 있으면 서버와 두 벌이 되어 다섯 번째 분류가 조용히 생긴다.
 *
 * <p>항목은 <b>묶음을 거쳐서만</b> 실린다({@link PrepSection}). 묶음을 하나도 안 쓰는 분류는
 * {@code label}이 {@code null}인 묶음 하나로 내려가고, 화면은 그때 소제목을 그리지 않는다
 * (#1358).
 *
 * <p>예전에는 같은 항목을 편 평면 목록({@code items})이 나란히 실렸다. 응답에서 그것을
 * 없앤 #1360이 이미 열려 있던 탭을 깨뜨렸기 때문에 한시로 되살린 자리였고, 기기를 전부
 * 새로고침한 것이 확인돼 지웠다(#1362).
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
