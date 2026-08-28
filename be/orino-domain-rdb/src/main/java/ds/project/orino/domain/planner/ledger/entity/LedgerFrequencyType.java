package ds.project.orino.domain.planner.ledger.entity;

/**
 * 주기(확정 명세 §6.2). 여섯 가지가 전부다.
 *
 * <p>필요한 부속 값이 종류마다 다르다 — {@code freqInterval}(N개월·N일의 N),
 * {@code freqDay}(일자 또는 요일), {@code freqMonth}(매년 M월). 없으면 규칙이 성립하지
 * 않으므로 생성 시점에 거부한다(LDG-ERR-012). 「일단 저장하고 전개할 때 보자」로 두면
 * 스케줄러가 도는 새벽에 조용히 아무것도 안 적힌다.
 */
public enum LedgerFrequencyType {

    /** 매주 {@code freqDay}요일(월=1). */
    WEEKLY,

    /** 매월 {@code freqDay}일. 31일 없는 달은 말일로 내려온다. */
    MONTHLY_DAY,

    /** 매월 말일. 2월이면 28·29일이다. */
    MONTHLY_LAST,

    /** {@code freqInterval}개월마다 {@code freqDay}일. 시작월을 기준으로 센다. */
    EVERY_N_MONTHS,

    /** 매년 {@code freqMonth}월 {@code freqDay}일. 2/29는 평년에 2/28로 내려온다. */
    YEARLY,

    /** {@code freqInterval}일마다. 시작일부터 센다. */
    EVERY_N_DAYS
}
