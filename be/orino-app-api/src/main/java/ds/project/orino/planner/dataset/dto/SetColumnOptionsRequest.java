package ds.project.orino.planner.dataset.dto;

import java.util.List;

/**
 * 열 허용값 목록(enum) 설정/해제 요청. 값 목록을 주거나 {@code null}·빈 목록(해제).
 *
 * <p>느슨한 제약이다 — 서버는 이 목록으로 셀 값을 <b>강제하지 않는다</b>. FE 드롭다운(datalist)
 * 편의를 위한 목록일 뿐이라, 붙여넣기·임포트·수식 결과가 목록 밖이어도 그대로 받는다. 서버는
 * 목록을 정규화(공백 제거·중복 제거)해 저장만 한다.
 */
public record SetColumnOptionsRequest(
        List<String> options
) {
}
