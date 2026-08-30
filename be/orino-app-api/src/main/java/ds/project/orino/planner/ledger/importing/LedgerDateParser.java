package ds.project.orino.planner.ledger.importing;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 소스마다 다른 날짜 표기를 하나로.
 *
 * <p>프리셋이 형식을 지정했으면 <b>그것만</b> 쓴다 — 지정한 형식이 안 맞으면 그건 매핑이
 * 틀렸다는 뜻이고, 다른 형식으로 슬쩍 성공시키면 사람은 매핑이 맞다고 믿은 채 다음 달에
 * 같은 실수를 반복한다.
 *
 * <p>지정이 없을 때만 흔한 표기를 차례로 시도한다. 순서가 곧 규칙이다 — <b>ISO를 맨 앞에</b>
 * 두어 {@code 2026-08-25}가 다른 해석으로 새지 않게 한다.
 */
final class LedgerDateParser {

    /**
     * 시도 순서.
     *
     * <p><b>{@code MM/dd/yyyy}는 넣지 않는다.</b> {@code 03/04/2026}이 3월 4일인지 4월 3일인지
     * 파일만 보고는 알 수 없고, 잘못 고르면 조용히 틀린 날짜가 몇 백 줄 들어간다.
     * 그런 파일은 프리셋에서 형식을 <b>명시하게</b> 한다.
     */
    private static final List<DateTimeFormatter> PATTERNS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyyy년 M월 d일"));

    private LedgerDateParser() {
    }

    static LocalDate parse(String raw, String pattern) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // 「2026-08-25 14:03:11」처럼 시각이 붙어 오는 소스가 있다. 날짜만 본다 —
        // 원장의 기준은 날짜이고, 시각은 있으면 좋은 부가 정보일 뿐이다.
        String value = raw.trim().split("[ T]")[0].trim();
        if (pattern != null && !pattern.isBlank()) {
            return tryParse(value, DateTimeFormatter.ofPattern(pattern.trim()));
        }
        for (DateTimeFormatter formatter : PATTERNS) {
            LocalDate parsed = tryParse(value, formatter);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static LocalDate tryParse(String value, DateTimeFormatter formatter) {
        try {
            return LocalDate.parse(value, formatter);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return null;
        }
    }
}
