package ds.project.orino.planner.travel.prep.dto;

import ds.project.orino.domain.planner.travel.entity.TripPrepItem;

import java.time.LocalDate;

/**
 * 준비 항목 한 줄(API §10).
 *
 * <p>분류는 여기 없다 — 항목은 항상 자기 분류 그룹 안에 실려 나간다({@link PrepGroup}).
 * 같은 값을 두 자리에 두면 그룹과 항목이 다른 분류를 말하는 응답이 만들어질 수 있다.
 *
 * <p>묶음 이름은 <b>여기에도 싣는다</b>({@link PrepSection}이 이미 갖고 있는데도). 편집 시트가
 * 항목 하나만 들고 열리고, 수정 요청도 항목 단위라 「지금 무슨 묶음인가」를 항목이 말할 수
 * 있어야 한다 — 분류와 달리 화면이 되짚어 찾을 이름이 아니다(#1358).
 *
 * @param dueDate 저장하지 않고 출발일에서 뺀 값. 그래서 출발일이 움직이면 함께 움직인다
 * @param overdue 첫날 기준 도시의 오늘로 판정한다. 체크한 항목은 지나지 않는다
 */
public record PrepItemView(
        Long id,
        String title,
        boolean done,
        String sectionLabel,
        Integer quantity,
        Integer dueDaysBefore,
        LocalDate dueDate,
        boolean overdue,
        String url,
        String memo,
        int displayOrder
) {

    public static PrepItemView of(TripPrepItem item, LocalDate startDate, LocalDate today) {
        return new PrepItemView(
                item.getId(),
                item.getTitle(),
                item.isDone(),
                item.getSectionLabel(),
                item.getQuantity(),
                item.getDueDaysBefore(),
                item.dueDate(startDate),
                item.isOverdue(startDate, today),
                item.getUrl(),
                item.getMemo(),
                item.getDisplayOrder());
    }
}
