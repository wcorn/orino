package ds.project.orino.planner.travel.day.dto;

import jakarta.validation.constraints.Size;

/**
 * 날짜 탭 롱프레스 시트가 보내는 것 — 기준 도시 변경과 도시 메모.
 *
 * <p><b>보낸 필드만 바꾼다.</b> 메모만 고치려는 요청이 기준 도시를 건드리면 안 되고, 그 반대도
 * 마찬가지다. 그래서 둘 다 선택이고 {@code null}은 "안 보냄"으로 읽는다.
 *
 * @param cityMemo 빈 문자열을 보내면 메모를 지운다({@code null}은 "안 보냄"과 구분된다)
 */
public record DayUpdateRequest(
        Long baseCityPlaceId,

        @Size(max = 200, message = "도시 메모는 200자를 넘을 수 없습니다.")
        String cityMemo
) {
}
