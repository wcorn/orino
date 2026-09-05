import { useQuery } from "@tanstack/react-query";

import {
  fetchAssetDetail,
  fetchAssets,
  fetchAssetTransactions,
  fetchAutoRules,
  fetchBalanceCurve,
  fetchBudget,
  fetchCalendar,
  fetchCards,
  fetchCategories,
  fetchDashboard,
  fetchFxRate,
  fetchImportBatches,
  fetchLedgerSummary,
  fetchPoints,
  fetchReceipts,
  fetchRecurring,
  fetchRecurringHistory,
  fetchSettings,
  fetchStatements,
  fetchStatementTransactions,
  fetchStats,
  fetchSuggestions,
  fetchTemplates,
  fetchTransactions,
  fetchUpcoming,
  type LedgerFlow,
  type LedgerPerspective,
  type TrendRange,
} from "../api/ledger";
import { ledgerKeys } from "../queryKeys";

/** 자산 목록. 잔액은 서버가 원장에서 파생해 준다 — 화면이 다시 더하지 않는다. */
export function useLedgerAssets(enabled = true) {
  return useQuery({
    queryKey: ledgerKeys.assets,
    queryFn: fetchAssets,
    staleTime: 30 * 1000,
    enabled,
  });
}

export function useLedgerAssetDetail(id: number, range: TrendRange) {
  return useQuery({
    queryKey: ledgerKeys.asset(id, range),
    queryFn: () => fetchAssetDetail(id, range),
    staleTime: 30 * 1000,
    enabled: Number.isFinite(id),
  });
}

export function useLedgerAssetTransactions(id: number) {
  return useQuery({
    queryKey: ledgerKeys.assetTransactions(id),
    queryFn: () => fetchAssetTransactions(id),
    staleTime: 30 * 1000,
    enabled: Number.isFinite(id),
  });
}

/**
 * 카테고리. **처음 부르는 순간 서버가 기본 프리셋 13종을 심는다**(D-14) —
 * 그래서 입력 모달이 열리자마자 고를 것이 있다.
 */
export function useLedgerCategories(flow?: LedgerFlow) {
  return useQuery({
    queryKey: ledgerKeys.categories(flow),
    queryFn: () => fetchCategories(flow),
    // 카테고리는 자주 바뀌지 않는다. 입력 모달이 열릴 때마다 다시 부르지 않는다.
    staleTime: 5 * 60 * 1000,
  });
}

export function useLedgerTransactions(
  from?: string,
  to?: string,
  trip: { tripId?: number; excludeTrip?: boolean } = {},
) {
  const { tripId, excludeTrip } = trip;
  return useQuery({
    queryKey: ledgerKeys.transactions(from, to, tripId, excludeTrip),
    queryFn: () => fetchTransactions({ from, to, tripId, excludeTrip }),
    staleTime: 30 * 1000,
  });
}

/** 대시보드. v1.5 블록은 서버가 아예 안 내린다 — 화면도 그 자리를 그리지 않는다. */
export function useLedgerDashboard() {
  return useQuery({
    queryKey: ledgerKeys.dashboard,
    queryFn: fetchDashboard,
    staleTime: 30 * 1000,
  });
}

export function useLedgerStats(
  period?: string,
  perspective?: LedgerPerspective,
) {
  return useQuery({
    queryKey: ledgerKeys.stats(period, perspective),
    queryFn: () => fetchStats(period, perspective),
    staleTime: 30 * 1000,
  });
}

/**
 * 예상 잔액 곡선(§8.4).
 *
 * <p>예정 목록과 <b>같은 계산</b>을 서버에서 쓴다 — 곡선이 따로 세면 두 화면이 다른 말을 한다.
 */
export function useBalanceCurve(days = 30) {
  return useQuery({
    queryKey: ledgerKeys.balanceCurve(days),
    queryFn: () => fetchBalanceCurve(days),
    staleTime: 30 * 1000,
  });
}

/** 빠른 입력 템플릿. 많이 쓴 순으로 온다 — 대시보드 칩이 이 순서를 그대로 쓴다. */
export function useLedgerTemplates() {
  return useQuery({
    queryKey: ledgerKeys.templates,
    queryFn: fetchTemplates,
    staleTime: 60 * 1000,
  });
}

/** 영수증 첨부. 거래를 열었을 때만 부른다 — 목록에서는 필요 없다. */
export function useLedgerReceipts(transactionId: number | null) {
  return useQuery({
    queryKey: ledgerKeys.receipts(transactionId ?? 0),
    queryFn: () => fetchReceipts(transactionId as number),
    enabled: transactionId != null,
    staleTime: 60 * 1000,
  });
}

export function useLedgerSettings() {
  return useQuery({
    queryKey: ledgerKeys.settings,
    queryFn: fetchSettings,
    staleTime: 5 * 60 * 1000,
  });
}

export function useLedgerSummary(enabled = true) {
  return useQuery({
    queryKey: ledgerKeys.summary,
    queryFn: fetchLedgerSummary,
    staleTime: 30 * 1000,
    enabled,
  });
}

/** 내용 자동완성. 두 글자부터 부른다 — 한 글자로는 후보가 너무 넓다. */
export function useTransactionSuggestions(keyword: string) {
  const trimmed = keyword.trim();
  return useQuery({
    queryKey: ledgerKeys.suggestions(trimmed),
    queryFn: () => fetchSuggestions(trimmed),
    enabled: trimmed.length >= 2,
    staleTime: 60 * 1000,
  });
}

/**
 * 환율. **못 가져와도 실패가 아니다** — `rate: null`이 오고 화면은 직접 입력을 받는다.
 * 원화면 부르지 않는다.
 */
export function useFxRate(currency: string | null) {
  return useQuery({
    queryKey: ledgerKeys.fxRate(currency ?? ""),
    queryFn: () => fetchFxRate(currency as string),
    enabled: currency != null && currency !== "KRW",
    staleTime: 60 * 60 * 1000,
    // 고시가 없는 것은 재시도로 해결되지 않는다. 기다리게 두면 입력이 멈춘다.
    retry: false,
  });
}

/**
 * 예정 목록 — 네 출처의 UNION.
 *
 * <p>캐시하지 않는다는 서버 쪽 결정과 짝을 이룬다: 12개월 × 정기 항목 20개면 240행 수준이라
 * 그냥 다시 부른다. 대신 <b>일수를 키에 넣어</b> 「더 보기」로 넓혔다가 돌아와도 즉시 그려진다.
 */
export function useLedgerUpcoming(days = 30) {
  return useQuery({
    queryKey: ledgerKeys.upcoming(days),
    queryFn: () => fetchUpcoming(days),
    staleTime: 30 * 1000,
  });
}

/** 캘린더. 달을 옮길 때마다 다른 캐시여야 뒤로 돌아왔을 때 이전 달이 즉시 그려진다. */
export function useLedgerCalendar(month: string, enabled = true) {
  return useQuery({
    queryKey: ledgerKeys.calendar(month),
    queryFn: () => fetchCalendar(month),
    staleTime: 30 * 1000,
    enabled,
  });
}

/** 예산. 대시보드의 2단 게이지와 예산 화면이 같은 값을 읽는다. */
export function useLedgerBudget(period?: string) {
  return useQuery({
    queryKey: ledgerKeys.budget(period),
    queryFn: () => fetchBudget(period),
    staleTime: 30 * 1000,
  });
}

/** 카드 목록. 사이드바가 가리키는 곳이자 청구서로 들어가는 입구다(D-11). */
export function useLedgerCards() {
  return useQuery({
    queryKey: ledgerKeys.cards,
    queryFn: fetchCards,
    staleTime: 30 * 1000,
  });
}

export function useLedgerStatements(cardId: number) {
  return useQuery({
    queryKey: ledgerKeys.statements(cardId),
    queryFn: () => fetchStatements(cardId),
    staleTime: 30 * 1000,
    enabled: Number.isFinite(cardId),
  });
}

/** 그 청구서에 편입된 거래들. 「왜 이 금액인가」의 마지막 한 단계다. */
export function useStatementTransactions(statementId: number | null) {
  return useQuery({
    queryKey: ledgerKeys.statementTransactions(statementId ?? 0),
    queryFn: () => fetchStatementTransactions(statementId as number),
    enabled: statementId != null,
    staleTime: 30 * 1000,
  });
}

/** 정기 항목. 목록·스탯·점검 신호·미납이 한 응답에 온다 — 이 화면은 점검 도구다. */
export function useLedgerRecurring() {
  return useQuery({
    queryKey: ledgerKeys.recurring,
    queryFn: fetchRecurring,
    staleTime: 30 * 1000,
  });
}

/** 금액 변경 이력 + 미발생 이력. 열었을 때만 부른다. */
export function useRecurringHistory(id: number | null) {
  return useQuery({
    queryKey: ledgerKeys.recurringHistory(id ?? 0),
    queryFn: () => fetchRecurringHistory(id as number),
    enabled: id != null,
    staleTime: 60 * 1000,
  });
}

/** 가져오기 이력. 되돌린 배치도 함께 온다 — 무엇을 물렀는지도 이력이다. */
export function useImportBatches() {
  return useQuery({
    queryKey: ledgerKeys.importBatches,
    queryFn: fetchImportBatches,
    staleTime: 30 * 1000,
  });
}

/** 자동 분류 규칙. 가져오기와 수동 입력이 같은 목록을 쓴다. */
export function useAutoRules() {
  return useQuery({
    queryKey: ledgerKeys.autoRules,
    queryFn: fetchAutoRules,
    staleTime: 60 * 1000,
  });
}

/** 포인트. 자산 조회와 **따로** 부른다 — 같은 응답에 실으면 언젠가 합계에 섞인다. */
export function usePoints() {
  return useQuery({
    queryKey: ledgerKeys.points,
    queryFn: fetchPoints,
    staleTime: 60 * 1000,
  });
}
