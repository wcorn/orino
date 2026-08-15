package ds.project.orino.planner.travel.move.dto;

import ds.project.orino.domain.planner.travel.entity.TravelMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 이동 저장(#1208). 등록과 수정이 같은 형태를 쓴다(전체 수정).
 *
 * <p><b>양 끝을 일정 id로 받는다.</b> 저장은 장소 쌍에 하지만 화면은 일정을 보고 있고, 서버가
 * 일정 → 장소로 옮겨 준다. 화면이 장소 id를 들고 다니면 저장 단위를 바꿀 때마다 화면이 함께
 * 흔들린다.
 *
 * @param fromActivityId 출발 일정
 * @param toActivityId   도착 일정. 숙소로 가는 이동이면 대신 {@code toStayId}를 보낸다
 * @param toStayId       도착 숙소. {@code toActivityId}와 <b>정확히 하나만</b> 보낸다
 * @param name           실제로 무엇을 타는지({@code 나리타 익스프레스 3호}). 수단 분류만으로는
 *                       현지에서 무엇을 찾아야 할지 알 수 없다
 * @param durationMinutes 소요 시간(분). <b>생략할 수 있다</b> — 수단을 먼저 정하고 시간은 나중에
 *                       확인하는 것이 실제 순서다
 */
public record MoveWriteRequest(

        @NotNull(message = "출발 일정을 지정해 주세요.")
        Long fromActivityId,

        Long toActivityId,

        Long toStayId,

        @NotNull(message = "이동수단을 선택해 주세요.")
        TravelMode mode,

        @Size(max = 100, message = "이동수단 이름은 100자를 넘을 수 없습니다.")
        String name,

        // 상한은 7일이다. 페리·대륙 횡단 열차까지 담으면서, 시(時)를 분으로 잘못 넣은 값은
        // 걸러 낸다. 0분은 이동이 아니라 오타에 가까우므로 하한은 1분이다.
        @Min(value = 1, message = "소요 시간은 1분 이상이어야 합니다.")
        @Max(value = 10080, message = "소요 시간이 너무 깁니다.")
        Integer durationMinutes,

        @Size(max = 500, message = "링크가 너무 깁니다.")
        String url,

        @Size(max = 500, message = "메모는 500자를 넘을 수 없습니다.")
        String memo
) {

    /** 도착지가 정확히 하나인가. 둘 다 오거나 둘 다 없으면 어디로 가는 이동인지 알 수 없다. */
    public boolean hasExactlyOneDestination() {
        return (toActivityId == null) != (toStayId == null);
    }
}
