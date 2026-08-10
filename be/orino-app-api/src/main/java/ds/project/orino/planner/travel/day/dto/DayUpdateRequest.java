package ds.project.orino.planner.travel.day.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * 날짜 탭 롱프레스 시트가 보내는 것 — 기준 도시 변경과 도시 메모.
 *
 * <p><b>보낸 필드만 바꾼다.</b> 메모만 고치려는 요청이 기준 도시를 건드리면 안 되고, 그 반대도
 * 마찬가지다. 그래서 전부 선택이고 {@code null}은 "안 보냄"으로 읽는다.
 *
 * <p>도시는 구간 입력과 <b>같은 두 가지 방식</b>으로 지정한다({@code TripLegRequest}) — 이미
 * 담아 둔 도시({@code baseCityPlaceId})거나 검색 결과 그대로({@code baseCityGooglePlaceId})다.
 * 뒤쪽이 없으면 여행에 아직 없는 도시로 하루를 옮길 때 화면이 먼저 장소를 만들어야 하는데,
 * 그렇게 만든 도시에는 <b>도시 식별자가 없어</b> 그날 일정이 전부 "다른 도시"로 표시된다.
 *
 * @param cityMemo 빈 문자열을 보내면 메모를 지운다({@code null}은 "안 보냄"과 구분된다)
 */
public record DayUpdateRequest(
        Long baseCityPlaceId,

        String baseCityGooglePlaceId,

        @Size(max = 200, message = "도시 메모는 200자를 넘을 수 없습니다.")
        String cityMemo
) {

    /** 둘 다 오면 어느 쪽이 맞는지 서버가 정할 수 없다. 둘 다 없는 것은 "도시 안 바꿈"이다. */
    @JsonIgnore
    @AssertTrue(message = "기준 도시는 baseCityPlaceId 또는 baseCityGooglePlaceId 중 하나로 지정해 주세요.")
    public boolean isCityGivenAtMostOnce() {
        return !(baseCityPlaceId != null && hasGoogleId());
    }

    @JsonIgnore
    public boolean hasGoogleId() {
        return baseCityGooglePlaceId != null && !baseCityGooglePlaceId.isBlank();
    }

    @JsonIgnore
    public boolean hasCity() {
        return baseCityPlaceId != null || hasGoogleId();
    }
}
