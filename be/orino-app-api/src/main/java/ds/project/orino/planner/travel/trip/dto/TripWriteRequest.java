package ds.project.orino.planner.travel.trip.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 여행 생성·수정 요청. 생성과 수정이 같은 필드를 쓰므로(전체 수정) 한 타입으로 둔다.
 *
 * <p>날짜는 벽시계 값이다 — UTC 오프셋을 붙이지 않는다. 기준 타임존은 여행이 아니라
 * <b>그 날짜의 기준 도시</b>가 갖는다.
 *
 * <p><b>v2.1 — 목적지 하나가 아니라 구간 목록을 받는다.</b> 타임존·통화·좌표는 받지 않는다.
 * 도시가 그 값들의 주인이라, 여행이 따로 들고 있으면 도시를 옮겨 다닐 때 서로 어긋난다.
 *
 * @param title                 최대 50자, 필수. 목적지가 여행에 없으니 자동으로 채울 이름도 없다
 * @param legs                  구간 목록(도시 + 일수). 생성에는 반드시 있어야 하고,
 *                              수정에서 생략하면 날짜별 기준 도시를 건드리지 않는다
 * @param defaultNotifyMinutes  여행 단위 기본 알림 시점(분 전). 생략 시 기존값 유지
 * @param morningSummaryEnabled 아침 요약 알림. 생략 시 기존값 유지
 * @param confirmArchive        기간 단축으로 잘리는 일정을 보관함으로 옮겨도 좋다는 확인.
 *                              없으면 서버가 409로 거부한다
 */
public record TripWriteRequest(
        @NotBlank(message = "여행 제목을 입력해 주세요.")
        @Size(max = 50, message = "제목은 50자를 넘을 수 없습니다.")
        String title,

        @NotNull(message = "시작일을 입력해 주세요.")
        LocalDate startDate,

        @NotNull(message = "종료일을 입력해 주세요.")
        LocalDate endDate,

        @Valid
        @NotEmpty(groups = OnCreate.class, message = "구간을 하나 이상 입력해 주세요.")
        List<TripLegRequest> legs,

        @Positive(message = "알림 시점은 0보다 커야 합니다.")
        Integer defaultNotifyMinutes,

        Boolean morningSummaryEnabled,
        Boolean confirmArchive
) {

    /**
     * 생성에만 거는 검증 그룹. 구간은 <b>생성에서 필수</b>지만 수정에서는 생략할 수 있다 —
     * 알림 설정만 바꾸려는 요청이 도시 배치를 되감지 않게 하려면 "안 보냄"과 "비움"을
     * 구분해야 한다.
     */
    public interface OnCreate {
    }

    public boolean archiveConfirmed() {
        return Boolean.TRUE.equals(confirmArchive);
    }

    /**
     * 구간을 보냈는가. 수정에서 생략하면 날짜별 기준 도시는 그대로 두고 기간만 맞춘다 —
     * 알림 설정만 바꾸려는 요청이 도시 배치를 통째로 되감으면 안 된다.
     */
    public boolean hasLegs() {
        return legs != null && !legs.isEmpty();
    }
}
