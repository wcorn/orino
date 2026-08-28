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
    // 장소가 붙지 않은 일정은 이동의 양 끝이 될 수 없다 — 어디서 어디로인지가 없다.
    // 이름은 바뀌었지만(#1208 이동시간 계산 → 이동 기록) 자리는 같아 코드는 그대로 둔다.
    TRAVEL_MOVE_NOT_AVAILABLE("TRAVEL-ERR-009",
            "두 일정 사이의 이동을 저장할 수 없습니다.", 400),
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
    TRAVEL_NOT_A_CITY("TRAVEL-ERR-016", "도시로 등록된 장소만 기준 도시가 될 수 있습니다.", 400),
    // 겹침을 허용하면 "오늘 밤 어디서 자는가"에 답이 둘이 되고, 화면은 그중 하나를 임의로
    // 고를 수밖에 없다. 체크아웃일 == 다음 체크인일은 겹침이 아니다(이동일).
    TRAVEL_STAY_OVERLAP("TRAVEL-ERR-017", "이미 숙소가 잡힌 기간입니다.", 409),
    // 018(도시 간 이동의 출발 알림)은 4단계 자리다 — 비워 둔다.
    TRAVEL_DAY_NOT_FOUND("TRAVEL-ERR-019", "존재하지 않는 여행 날짜입니다.", 404),
    TRAVEL_STAY_NOT_FOUND("TRAVEL-ERR-020", "존재하지 않는 숙소입니다.", 404),

    /**
     * 구글이 호출을 거절했다(할당량·키·과금). 503인 이유는 <b>사용자가 잘못한 게 없기</b>
     * 때문이다 — 검색어를 바꾸면 될 것처럼 400을 주면 바꿀 때마다 또 거절당한다.
     */
    TRAVEL_EXTERNAL_REJECTED("TRAVEL-ERR-021",
            "지금은 장소 정보를 새로 가져올 수 없어요. 이미 담아 둔 장소는 그대로 쓸 수 있어요.",
            503),

    // SHORTLINK (단축 URL)
    // 이 코드들은 전부 관리 API 전용이다. 공개 리다이렉트(/r/**)는 envelope를 쓰지 않고
    // HTML 404 한 장으로만 답한다 — 에러 코드를 내보내는 순간 그것 자체가 정보다(명세 §7).
    SHORTLINK_INVALID_TARGET("SL-ERR-001", "지원하지 않는 주소 형식입니다.", 400),
    SHORTLINK_SELF_REFERENCE("SL-ERR-002", "목적지가 단축 주소일 수 없습니다.", 400),
    // 삭제한 링크의 슬러그에도 이 코드가 난다. 그게 영구 점유다(명세 §3.1) —
    // 삭제된 것인지 살아 있는 것인지 구분해 알려주지 않는다.
    SHORTLINK_SLUG_TAKEN("SL-ERR-003", "이미 사용 중인 주소입니다.", 409),
    SHORTLINK_INVALID_SLUG("SL-ERR-004", "사용할 수 없는 문자가 있습니다.", 400),
    SHORTLINK_SLUG_EXHAUSTED("SL-ERR-005", "주소를 만들지 못했습니다. 다시 시도해 주세요.", 500),
    SHORTLINK_NOT_FOUND("SL-ERR-006", "존재하지 않는 링크입니다.", 404),

    // LEDGER (가계부)
    // 여기서 막지 않으면 원장이 조용히 틀어진다. 잘못된 값을 받아 두고 나중에 고치는 길이
    // 이 모듈에는 없다 — 어긋남은 월말 대사에서야 드러나고, 그때는 어느 줄이 원인인지 모른다.
    LEDGER_ASSET_NOT_FOUND("LDG-ERR-001", "존재하지 않는 자산입니다.", 404),
    // 모든 거래는 자산에 붙는다(확정 명세 §3-1). 이 제약 하나가 카드별·은행별 뷰와
    // 잔액 정합성을 전부 만든다.
    LEDGER_ASSET_REQUIRED("LDG-ERR-002", "자산 없이는 거래를 만들 수 없습니다.", 400),
    LEDGER_TRANSFER_SAME_ASSET("LDG-ERR-003", "출금과 입금 자산이 같습니다.", 400),
    LEDGER_TRANSFER_COUNTER_REQUIRED("LDG-ERR-004", "이체는 대상 자산이 필요합니다.", 400),
    LEDGER_CATEGORY_FLOW_MISMATCH("LDG-ERR-005", "카테고리 종류가 거래 유형과 다릅니다.", 400),
    LEDGER_TRANSACTION_NOT_FOUND("LDG-ERR-006", "존재하지 않는 거래입니다.", 404),
    LEDGER_CATEGORY_CYCLE("LDG-ERR-014", "카테고리를 자기 하위로 옮길 수 없습니다.", 400),
    LEDGER_CATEGORY_TOO_DEEP("LDG-ERR-015", "카테고리는 2단까지만 만들 수 있습니다.", 400),
    // 연결 계좌 없는 체크카드는 잔액이 어디서도 빠지지 않는 유령 자산이 된다(D-4).
    LEDGER_DEBIT_CARD_LINK_REQUIRED("LDG-ERR-019", "체크카드는 연결 계좌가 필요합니다.", 400),
    LEDGER_FX_UNSUPPORTED_CURRENCY("LDG-ERR-020", "환율 고시에 없는 통화입니다.", 400),
    // 셋 중 하나만 오면 거부한다. 환산 근거가 반쪽이면 나중에 검증할 수 없다.
    LEDGER_FX_INCOMPLETE("LDG-ERR-021", "외화 정보는 통화·금액·환율이 함께 있어야 합니다.", 400),
    // 022는 명세 표에 없던 자리다. 자산에는 「존재하지 않는다」(001)가 있는데 카테고리에는
    // 없어서, 없는 카테고리를 붙이면 흐름 불일치(005)로 둘러대야 했다 — 원인을 감추는 코드다.
    LEDGER_CATEGORY_NOT_FOUND("LDG-ERR-022", "존재하지 않는 카테고리입니다.", 404),

    // 카드 청구서 — 여기가 이 모듈의 심장이라 실패도 또렷해야 한다.
    LEDGER_STATEMENT_NOT_FOUND("LDG-ERR-007", "존재하지 않는 청구서입니다.", 404),
    LEDGER_STATEMENT_ALREADY_PAID("LDG-ERR-008", "이미 납부가 끝난 청구서입니다.", 409),
    LEDGER_PAYMENT_EXCEEDS_BILLED("LDG-ERR-009", "결제 금액이 청구액을 넘습니다.", 400),
    LEDGER_NOT_A_CREDIT_CARD("LDG-ERR-010", "신용카드가 아닌 자산입니다.", 400),
    LEDGER_INSTALLMENT_MONTHS_OUT_OF_RANGE("LDG-ERR-016", "할부 개월 수가 범위를 벗어났습니다.", 400);

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
