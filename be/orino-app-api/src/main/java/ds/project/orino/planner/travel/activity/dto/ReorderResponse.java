package ds.project.orino.planner.travel.activity.dto;

import java.util.List;

/**
 * 순서 변경 결과. 재계산된 이동시간({@code legs})을 담아 드래그 직후 화면이 다시 조회하지 않고도
 * 이동시간을 갱신할 수 있게 한다.
 *
 * <p>이동시간은 장소·Routes가 붙는 2단계 기능이라 1단계에서는 항상 비어 있다. 그래도 형태를
 * 지금 맞춰 두면 2단계에서 FE 계약이 바뀌지 않는다.
 */
public record ReorderResponse(List<Object> legs) {

    public static ReorderResponse empty() {
        return new ReorderResponse(List.of());
    }
}
