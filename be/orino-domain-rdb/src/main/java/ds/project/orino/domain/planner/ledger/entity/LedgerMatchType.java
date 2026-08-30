package ds.project.orino.domain.planner.ledger.entity;

/**
 * 자동 분류 규칙이 내용을 견주는 방식(`LDG-062`).
 *
 * <p><b>정규식은 두지 않는다.</b> 「왜 이 카테고리가 붙었나」를 사람이 읽을 수 없으면 분류
 * 결과를 믿지 못하고, 믿지 못하는 자동 분류는 손으로 다시 고치게 되어 없느니만 못하다.
 */
public enum LedgerMatchType {

    CONTAINS,
    STARTS_WITH,
    EQUALS;

    /** 대소문자·앞뒤 공백은 무시한다 — 소스마다 표기가 달라 그것까지 사람이 맞출 수 없다. */
    public boolean matches(String title, String keyword) {
        if (title == null || keyword == null) {
            return false;
        }
        String haystack = title.trim().toLowerCase();
        String needle = keyword.trim().toLowerCase();
        if (needle.isEmpty()) {
            return false;
        }
        return switch (this) {
            case CONTAINS -> haystack.contains(needle);
            case STARTS_WITH -> haystack.startsWith(needle);
            case EQUALS -> haystack.equals(needle);
        };
    }
}
