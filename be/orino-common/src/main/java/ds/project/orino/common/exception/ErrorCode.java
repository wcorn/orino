package ds.project.orino.common.exception;

public enum ErrorCode {

    // GLOBAL
    BAD_REQUEST("GLB-ERR-001", "잘못된 요청입니다.", 400),
    METHOD_NOT_ALLOWED("GLB-ERR-002", "허용되지 않은 메서드입니다.", 405),
    INTERNAL_SERVER_ERROR("GLB-ERR-003", "내부 서버 오류입니다.", 500),

    // AUTH
    INVALID_CREDENTIALS("AUTH-ERR-001", "아이디 또는 비밀번호가 올바르지 않습니다.", 401),
    INVALID_TOKEN("AUTH-ERR-002", "유효하지 않은 토큰입니다.", 401),

    // STUDY PLANNER
    RESOURCE_NOT_FOUND("SP-ERR-001", "존재하지 않는 리소스입니다.", 404),
    INVALID_REQUEST("SP-ERR-002", "유효하지 않은 요청입니다.", 400),
    INVALID_STATE("SP-ERR-003", "현재 상태에서 수행할 수 없는 작업입니다.", 409),
    DUPLICATE_COLUMN_LABEL("SP-ERR-004", "이미 있는 열 이름입니다.", 409),
    FORMULA_SYNTAX_ERROR("SP-ERR-005", "수식을 이해할 수 없습니다.", 400),
    FORMULA_CIRCULAR_REFERENCE("SP-ERR-006", "수식이 자기 자신을 참조합니다.", 409),
    FORMULA_PROPAGATION_TOO_WIDE("SP-ERR-007", "이 편집이 다시 계산할 수식이 너무 많습니다.", 409),

    // PLANNER (Google Calendar 연동)
    ROUTINE_INVALID_RULE("PLN-ERR-002", "유효하지 않은 반복 규칙입니다.", 400),
    GOOGLE_NOT_CONNECTED("PLN-ERR-003", "Google 연동이 필요합니다.", 409),
    GOOGLE_API_FAILED("PLN-ERR-004", "Google API 호출에 실패했습니다.", 502),
    GOOGLE_INVALID_GRANT("PLN-ERR-005", "Google 연동이 만료되어 재연결이 필요합니다.", 401),

    // LIFELOG (일상기록)
    LIFELOG_GEOCODING_FAILED("LIFELOG-ERR-001", "장소 정보를 가져오지 못했습니다.", 502),
    LIFELOG_MOMENT_NOT_FOUND("LIFELOG-ERR-002", "존재하지 않는 기록입니다.", 404),
    LIFELOG_EMPTY_MOMENT("LIFELOG-ERR-003", "본문이나 사진 중 하나는 있어야 합니다.", 400),
    LIFELOG_INVALID_COORDINATE("LIFELOG-ERR-004", "위도와 경도는 함께 지정해야 합니다.", 400),
    LIFELOG_FLOW_NOT_FOUND("LIFELOG-ERR-005", "존재하지 않는 흐름입니다.", 404),

    // TRAVEL (여행)
    // 소유권 실패도 404다 — 403으로 답하면 "그 id의 여행은 있다"는 사실이 새어나간다.
    TRAVEL_TRIP_NOT_FOUND("TRAVEL-ERR-001", "존재하지 않는 여행입니다.", 404),
    TRAVEL_INVALID_PERIOD("TRAVEL-ERR-002", "종료일은 시작일보다 빠를 수 없습니다.", 400),
    TRAVEL_INVALID_TIMEZONE("TRAVEL-ERR-003", "유효하지 않은 시간대입니다.", 400),
    TRAVEL_INVALID_CURRENCY("TRAVEL-ERR-004", "유효하지 않은 통화 코드입니다.", 400),
    // 기간을 줄이면 잘린 일정이 보관함으로 밀린다. 사용자가 모르고 잃지 않도록 확인을 요구한다.
    TRAVEL_ARCHIVE_CONFIRM_REQUIRED("TRAVEL-ERR-005",
            "기간을 줄이면 일부 일정이 미배정 보관함으로 이동합니다.", 409),
    TRAVEL_ACTIVITY_NOT_FOUND("TRAVEL-ERR-006", "존재하지 않는 일정입니다.", 404),
    TRAVEL_DATE_OUT_OF_RANGE("TRAVEL-ERR-007", "여행 기간 밖의 날짜입니다.", 400),
    TRAVEL_PLACE_NOT_FOUND("TRAVEL-ERR-008", "존재하지 않는 장소입니다.", 404),
    TRAVEL_TIME_NOT_AVAILABLE("TRAVEL-ERR-009",
            "두 일정 사이의 이동시간을 계산할 수 없습니다.", 400),
    TRAVEL_FX_UNAVAILABLE("TRAVEL-ERR-010", "환율을 가져올 수 없습니다.", 503),
    TRAVEL_FX_UNSUPPORTED_CURRENCY("TRAVEL-ERR-011",
            "지원하지 않는 통화입니다.", 400),
    // 404가 아니다 — 일정은 실재하고, 아직 기록할 때가 아닐 뿐이다.
    TRAVEL_LOG_BEFORE_TRIP("TRAVEL-ERR-012",
            "여행이 시작된 뒤에 기록할 수 있습니다.", 400),
    TRAVEL_PHOTO_LIMIT_EXCEEDED("TRAVEL-ERR-013",
            "사진은 일정당 10장까지 올릴 수 있습니다.", 400),
    TRAVEL_PHOTO_NOT_FOUND("TRAVEL-ERR-014", "존재하지 않는 사진입니다.", 404),
    // 015는 도시 경계 이동시간(3단계) 자리다 — 번호를 당기지 않고 비워 둔다.
    // 도시가 아닌 장소를 기준 도시로 지정하면 타임존은 우연히 맞아도 도시 일치 판정이
    // 그날 일정을 전부 "다른 도시"로 만든다.
    TRAVEL_NOT_A_CITY("TRAVEL-ERR-016", "도시로 등록된 장소만 기준 도시가 될 수 있습니다.", 400);

    private final String code;
    private final String message;
    private final int httpStatus;

    ErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getMessage(Throwable e) {
        return message + " - " + e.getMessage();
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getMessage(String additionalMessage) {
        if (additionalMessage != null && !additionalMessage.isBlank()) {
            return message + " - " + additionalMessage;
        }
        return message;
    }
}
