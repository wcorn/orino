import type { UpcomingKind } from "../api/ledger";

/**
 * 잔액·예정을 **화면에 그리기 위한** 계산만 있다.
 *
 * <p><b>판정은 서버 값을 쓴다</b>([D-13](https://github.com/wcorn/orino/wiki/Ledger-Open-Items)).
 * 「미납인가」·「잔액이 얼마인가」·「이번 달 얼마 쓸 건가」는 전부 서버가 정한 값이고 여기서
 * 다시 계산하지 않는다 — 두 곳에서 세면 화면과 API가 다른 말을 하게 되고, 이 모듈에서 그건
 * 「원장이 틀어졌다」와 구분되지 않는다.
 *
 * <p>여기 있는 것은 그 값을 <b>퍼센트와 라벨로 바꾸는 일</b>뿐이다.
 */

/** `D-3` · `D-DAY` · `D+2`(지났다). 예정 줄과 다가오는 결제가 같은 표기를 쓴다. */
export function formatDday(dday: number): string {
  if (dday === 0) {
    return "D-DAY";
  }
  return dday > 0 ? `D-${dday}` : `D+${-dday}`;
}

export interface GaugeWidths {
  /** 이미 쓴 돈. 게이지의 진한 부분. */
  spent: number;
  /** 아직 안 썼지만 나갈 게 확정된 돈. 연한 부분. */
  scheduled: number;
}

/**
 * 2단 예산 게이지의 너비(%).
 *
 * <p>확정분만 칠하면 「아직 절반 남았네」 하다가 25일에 고정비가 빠지고 놀란다 —
 * 그래서 예정분이 <b>확정분 위에 이어 붙는다</b>(확정 명세 §8.2).
 *
 * <p>합이 100%를 넘지 않게 자른다. 예산을 넘긴 달에도 막대가 칸 밖으로 삐져나가면
 * 「얼마나 넘었나」는 숫자로 읽어야 하고 막대는 아무 말도 못 하게 된다.
 */
export function gaugeWidths(
  spent: number,
  scheduled: number,
  total: number,
): GaugeWidths {
  if (total <= 0) {
    return { spent: 0, scheduled: 0 };
  }
  const spentPct = Math.min((spent / total) * 100, 100);
  const scheduledPct = Math.min((scheduled / total) * 100, 100 - spentPct);
  return { spent: spentPct, scheduled: Math.max(scheduledPct, 0) };
}

/** 예정 종류의 사람 이름. 필터 칩과 배지가 같은 말을 쓴다. */
export const UPCOMING_KIND_LABELS: Record<UpcomingKind, string> = {
  RECURRING: "정기",
  ONE_OFF: "직접 예약",
  CARD_PAYMENT: "카드 대금",
  INSTALLMENT: "할부",
};

/**
 * 잔액 글자색. <b>음수만</b> 경고색이다.
 *
 * <p>전부 빨가면 정말 위험한 것이 묻힌다(화면 설계 §1) — 지출은 가계부에서 정상이고,
 * 잔액이 마이너스인 것만 사고다.
 */
export function balanceToneClass(amount: number): string {
  return amount < 0 ? "text-destructive" : "text-foreground";
}
