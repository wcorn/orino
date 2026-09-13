import type { AssetView, SavingsKind } from "../api/ledger";

/** 예·적금 종류 셀렉트. 빈 값이 일반이다 — 서버의 `null`과 같은 뜻. */
export const SAVINGS_KIND_OPTIONS: {
  value: SavingsKind | "";
  label: string;
}[] = [
  { value: "", label: "일반" },
  { value: "HOUSING_SUBSCRIPTION", label: "청약" },
];

/**
 * 청약인가. 유형까지 함께 본다 — 서버 `LedgerAsset.isHousingSubscription`과 같은 판정이다.
 * 종류만 보면 예·적금이 아닌 행을 청약으로 그릴 길이 남는다.
 */
export function isHousingSubscription(
  asset: Pick<AssetView, "type" | "savingsKind">,
): boolean {
  return (
    asset.type === "SAVINGS" && asset.savingsKind === "HOUSING_SUBSCRIPTION"
  );
}

/** `2026-06` → `2026년 6월`. 청약홈이 「몇 월분까지」로 말하는 단위 그대로 적는다. */
export function monthLabel(month: string): string {
  const [year, value] = month.split("-");
  return `${year}년 ${Number(value)}월`;
}

/** `250000` → `25만 원`. 월 인정 상한은 늘 만 원 단위라 이렇게 읽는 편이 빠르다. */
export function manwon(amount: number): string {
  return `${amount / 10000}만 원`;
}
