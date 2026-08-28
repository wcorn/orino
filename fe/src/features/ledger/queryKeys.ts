import type { LedgerFlow, TrendRange } from "./api/ledger";

/** 가계부 쿼리 키. 기존 `shortlinkKeys`·`travelKeys`와 같은 형태로 한곳에 묶는다. */
export const ledgerKeys = {
  all: ["ledger"] as const,
  summary: ["ledger", "summary"] as const,
  dashboard: ["ledger", "dashboard"] as const,
  stats: (period?: string) => ["ledger", "stats", period ?? ""] as const,
  settings: ["ledger", "settings"] as const,
  assets: ["ledger", "assets"] as const,
  asset: (id: number, range: TrendRange) =>
    ["ledger", "asset", id, range] as const,
  assetTransactions: (id: number) =>
    ["ledger", "asset", id, "transactions"] as const,
  categories: (flow?: LedgerFlow) =>
    ["ledger", "categories", flow ?? "ALL"] as const,
  /**
   * 내역 목록. 조회 구간이 키에 들어간다 — 월을 옮길 때마다 다른 캐시여야
   * 뒤로 돌아왔을 때 이전 달이 즉시 그려진다.
   */
  transactions: (from?: string, to?: string) =>
    ["ledger", "transactions", from ?? "", to ?? ""] as const,
  transactionLists: ["ledger", "transactions"] as const,
  suggestions: (keyword: string) => ["ledger", "suggest", keyword] as const,
  fxRate: (currency: string) => ["ledger", "fx", currency] as const,
};
