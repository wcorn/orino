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
 * @param total 분류 전체 개수. 묶음별 개수는 각 {@link PrepSection}이 갖는다
 * @param items <b>옛 클라이언트만 읽는 평면 목록</b>. {@link #sections}를 편 것과 같다 —
 *              지금 화면은 쓰지 않는다({@link #items}를 지우는 이슈는 #1362)
 */
public record PrepGroup(
        PrepCategory category,
        int total,
        int done,
        List<PrepItemView> items,
        List<PrepSection> sections
) {

    /**
     * 묶음만 받아 만든다. <b>{@code items}는 여기서 파생한다</b> — 두 벌을 각각 채우면
     * 언젠가 서로 다른 순서를 말하는 응답이 나온다(#1361).
     *
     * <p><b>이 필드는 한시적이다.</b> 응답에서 {@code items}를 없앤 #1360이 이미 열려 있던
     * 탭을 통째로 깨뜨렸다 — 이 앱의 서비스워커는 새 버전을 <b>사용자가 받아들여야</b> 갈고
     * (`registerType: "prompt"`), 앱 셸이 precache라 서버에서 파일이 사라져도 옛 번들은
     * 계속 돈다. 그래서 <b>BE가 먼저 모양을 바꾸면 그 화면은 새로고침 전까지 못 쓴다.</b>
     * 옛 번들이 도는 동안만 두고 #1362에서 지운다.
     */
    public static PrepGroup of(PrepCategory category, int total, int done,
                               List<PrepSection> sections) {
        return new PrepGroup(category, total, done,
                sections.stream().flatMap(section -> section.items().stream()).toList(),
                sections);
    }
}
