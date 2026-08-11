package ds.project.orino.planner.travel.activity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 일정 생성·수정 요청.
 *
 * <p>{@code sortOrder}를 받지 않는다 — 생성은 서버가 해당 날짜 끝에 붙이고, 순서 변경은
 * 드래그 결과를 한 번에 반영하는 별도 엔드포인트(`/activities/order`)가 맡는다.
 *
 * @param activityDate 여행 기간 내 날짜. {@code null}이면 미배정 보관함으로 넣는다
 * @param startTime    여행 타임존의 벽시계 시각({@code HH:mm}). {@code null} 허용 —
 *                     시각 없는 일정이 정상이라 알림만 못 걸릴 뿐이다
 */
public record ActivityWriteRequest(
        @NotBlank(message = "일정 제목을 입력해 주세요.")
        @Size(max = 100, message = "일정 제목은 100자를 넘을 수 없습니다.")
        String title,

        LocalDate activityDate,

        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,

        Long placeId,

        /**
         * 구글 장소를 그대로 담을 때 쓴다(S-06 "담기"). 서버가 {@code travel_place}에 upsert 한 뒤
         * {@code placeId}로 연결한다 — 화면이 장소를 먼저 저장하고 id를 들고 오지 않아도 되게.
         */
        String googlePlaceId,

        /**
         * (v2.1) 이 장소를 <b>어느 도시를 기준으로 찾았는지</b>(S-06 검색 기준 도시 칩).
         * {@code googlePlaceId}로 새로 담기는 장소에만 쓰이며, 그 도시의 식별자를 장소에 함께
         * 저장해 보관함 도시 그룹·도시 이탈 표시가 성립하게 한다.
         *
         * <p>없으면 장소는 도시 식별자 없이 저장된다 — 좌표로 도시를 추측하지 않는다(D-23).
         */
        Long cityPlaceId,

        @Size(max = 1000, message = "메모는 1000자를 넘을 수 없습니다.")
        String memo,

        @Size(max = 500, message = "링크는 500자를 넘을 수 없습니다.")
        String url,

        Boolean notifyEnabled,

        @Positive(message = "알림 시점은 0보다 커야 합니다.")
        Integer notifyMinutes,

        Boolean departureNotifyEnabled
) {

    public boolean notifyEnabledOrDefault() {
        return Boolean.TRUE.equals(notifyEnabled);
    }

    public boolean departureNotifyEnabledOrDefault() {
        return Boolean.TRUE.equals(departureNotifyEnabled);
    }
}
