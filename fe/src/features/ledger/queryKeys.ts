import type { LedgerFlow, TrendRange } from "./api/ledger";

/** 가계부 쿼리 키. 기존 `shortlinkKeys`·`travelKeys`와 같은 형태로 한곳에 묶는다. */
export const ledgerKeys = {
  all: ["ledger"] as const,
  summary: ["ledger", "summary"] as const,
  dashboard: ["ledger", "dashboard"] as const,
  templates: ["ledger", "templates"] as const,
  receipts: (transactionId: number) =>
    ["ledger", "receipts", transactionId] as const,
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
  /** 예정. 조회 일수가 키에 들어간다 — 「더 보기」가 다른 캐시여야 이전 범위로 즉시 돌아온다. */
  upcoming: (days: number) => ["ledger", "upcoming", days] as const,
  upcomingAll: ["ledger", "upcoming"] as const,
  calendar: (month: string) => ["ledger", "calendar", month] as const,
  budget: (period?: string) => ["ledger", "budget", period ?? ""] as const,
  cards: ["ledger", "cards"] as const,
  statements: (cardId: number) =>
    ["ledger", "cards", cardId, "statements"] as const,
  statementTransactions: (statementId: number) =>
    ["ledger", "statements", statementId, "transactions"] as const,
  recurring: ["ledger", "recurring"] as const,
  recurringHistory: (id: number) =>
    ["ledger", "recurring", id, "history"] as const,
  suggestions: (keyword: string) => ["ledger", "suggest", keyword] as const,
  fxRate: (currency: string) => ["ledger", "fx", currency] as const,
};
