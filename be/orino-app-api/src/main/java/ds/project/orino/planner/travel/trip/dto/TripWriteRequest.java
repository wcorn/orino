package ds.project.orino.planner.travel.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 여행 생성·수정 요청. 생성과 수정이 같은 필드를 쓰므로(전체 수정) 한 타입으로 둔다.
 *
 * <p>날짜는 여행 타임존의 벽시계 값이다 — UTC 오프셋을 붙이지 않는다.
 *
 * @param title                  최대 50자. 비우면 {@code destinationName}으로 채워 저장한다
 * @param destinationName        목적지 표시명(필수)
 * @param destinationPlaceId     2단계~. 1단계는 생략한다
 * @param timezone               IANA 타임존 ID. 상태·D-day·알림 환산의 기준이라 값 검증을 한다
 * @param currency               ISO 4217 3자리
 * @param lat                    목적지 좌표(날씨 기준점)
 * @param defaultNotifyMinutes   여행 단위 기본 알림 시점(분 전). 생략 시 기존값 유지
 * @param morningSummaryEnabled  아침 요약 알림. 생략 시 기존값 유지
 * @param confirmArchive         기간 단축으로 잘리는 일정을 보관함으로 옮겨도 좋다는 확인.
 *                               없으면 서버가 409로 거부한다
 */
public record TripWriteRequest(
        @Size(max = 50, message = "제목은 50자를 넘을 수 없습니다.")
        String title,

        @NotBlank(message = "목적지를 입력해 주세요.")
        @Size(max = 100, message = "목적지는 100자를 넘을 수 없습니다.")
        String destinationName,

        Long destinationPlaceId,

        @NotNull(message = "시작일을 입력해 주세요.")
        LocalDate startDate,

        @NotNull(message = "종료일을 입력해 주세요.")
        LocalDate endDate,

        @NotBlank(message = "시간대를 입력해 주세요.")
        String timezone,

        @NotBlank(message = "통화를 입력해 주세요.")
        String currency,

        BigDecimal lat,
        BigDecimal lng,

        @Positive(message = "알림 시점은 0보다 커야 합니다.")
        Integer defaultNotifyMinutes,

        Boolean morningSummaryEnabled,
        Boolean confirmArchive
) {

    /** 제목 컬럼 길이. 목적지명(100자)으로 채울 때 넘칠 수 있어 여기서 자른다. */
    private static final int TITLE_MAX = 50;

    /** 제목 미입력 시 목적지명으로 채운다 — 제목 없는 여행 카드를 만들지 않는다. */
    public String resolvedTitle() {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        String fallback = destinationName.trim();
        return fallback.length() <= TITLE_MAX ? fallback : fallback.substring(0, TITLE_MAX);
    }

    public boolean archiveConfirmed() {
        return Boolean.TRUE.equals(confirmArchive);
    }
}
