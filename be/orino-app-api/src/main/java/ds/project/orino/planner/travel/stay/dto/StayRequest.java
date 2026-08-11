package ds.project.orino.planner.travel.stay.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 숙소 등록·수정. 생성과 수정이 같은 형태를 쓴다(전체 수정).
 *
 * @param placeId       좌표·도시 판정에 쓴다. 직접 입력한 숙소면 생략한다 — 그때는 이동시간도
 *                      도시 일치 판정도 하지 않는다
 * @param googlePlaceId 검색 결과를 그대로 담을 때. 서버가 장소를 upsert해 {@code placeId}로
 *                      연결한다 — 화면이 장소를 먼저 만들고 id를 들고 오지 않아도 되게.
 *                      {@code placeId}와 함께 보내지 않는다(일정 담기와 같은 규칙)
 * @param cityPlaceId   이 숙소가 <b>어느 도시에 있는지</b>. 새로 담기는 장소에 그 도시의
 *                      식별자를 함께 저장해 숙소 이동의 도시 경계 판정이 성립하게 한다(§3.4).
 *                      기준 도시에서 넘겨받지 않고 <b>사용자가 고른다</b> — 닛코 당일치기 날의
 *                      기준 도시는 닛코지만 자는 곳은 도쿄일 수 있다
 * @param checkInTime   벽시계 시각. UTC로 바꾸지 않는다 — 15:00 체크인은 어느 도시에서든 15:00이다
 */
public record StayRequest(
        @NotBlank(message = "숙소 이름을 입력해 주세요.")
        @Size(max = 100, message = "숙소 이름은 100자를 넘을 수 없습니다.")
        String name,

        Long placeId,

        String googlePlaceId,

        Long cityPlaceId,

        @NotNull(message = "체크인 날짜를 입력해 주세요.")
        LocalDate checkInDate,

        @NotNull(message = "체크아웃 날짜를 입력해 주세요.")
        LocalDate checkOutDate,

        @JsonFormat(pattern = "HH:mm")
        LocalTime checkInTime,

        @JsonFormat(pattern = "HH:mm")
        LocalTime checkOutTime,

        @Size(max = 500, message = "예약 링크가 너무 깁니다.")
        String bookingUrl,

        @Size(max = 500, message = "메모는 500자를 넘을 수 없습니다.")
        String memo
) {
}
