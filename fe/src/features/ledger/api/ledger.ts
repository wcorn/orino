import { client } from "@/shared/api";

interface ApiEnvelope<T> {
  code: string;
  data: T;
}

/** 돈이 움직이는 방향. 거래의 `type`과 카테고리의 `flow`가 같은 값 공간을 쓴다. */
export type LedgerFlow = "EXPENSE" | "INCOME" | "TRANSFER";

/** 예정은 잔액을 바꾸지 않지만 숨기지도 않는다 — 같은 타임라인 위에 놓인다. */
export type TransactionStatus = "CONFIRMED" | "SCHEDULED";

export type TransactionSource =
  | "MANUAL"
  | "RECURRING"
  | "SCHEDULED_ONE_OFF"
  | "CARD_PAYMENT"
  | "INSTALLMENT"
  | "ADJUSTMENT"
  | "REFUND"
  | "IMPORT";

export type AssetType =
  | "CASH"
  | "CHECKING"
  | "SAVINGS"
  | "DEBIT_CARD"
  | "CREDIT_CARD"
  | "PREPAID";

export type AssetGroupKind = "BANK" | "CARD_ISSUER" | "ETC";

/**
 * 자산 한 줄.
 *
 * `balance`와 `unpaidAmount`는 **둘 다 채워지지 않는다**. 잔액을 갖는 자산이면 앞의 것,
 * 신용카드면 뒤의 것이고, **체크카드는 둘 다 `null`**이다 — 카드로 쓴 돈은 연결 계좌에서
 * 빠지므로 카드에도 잔액을 주면 같은 돈이 두 번 잡힌다.
 */
export interface AssetView {
  id: number;
  groupId: number | null;
  name: string;
  type: AssetType;
  accountLast4: string | null;
  displayOrder: number;
  hidden: boolean;
  closedReason: string | null;
  maturityDate: string | null;
  targetAmount: number | null;
  linkedAssetId: number | null;
  linkedAssetName: string | null;
  balance: number | null;
  unpaidAmount: number | null;
}

export interface AssetGroupView {
  /** `null`이면 그룹 없는 자산들의 「그 외」 묶음이다. */
  id: number | null;
  name: string;
  kind: AssetGroupKind;
  displayOrder: number;
  collapsed: boolean;
  subtotal: number;
  assets: AssetView[];
}

export interface AssetListResponse {
  groups: AssetGroupView[];
  /** 숨긴 자산. 목록 본문에서는 빠지되 사라지지 않는다. */
  hidden: AssetView[];
  totalAssets: number;
  liabilities: number;
  netWorth: number;
}

export type TrendRange = "DAY" | "MONTH" | "YEAR";

export interface TrendPoint {
  date: string;
  balance: number;
  income: number;
  expense: number;
}

export interface CategoryShare {
  /** `null`이면 미분류. 빼지 않는다 — 안 보이면 정리하지 않는다. */
  categoryId: number | null;
  categoryName: string | null;
  amount: number;
  count: number;
}

export interface AssetDetailResponse {
  asset: AssetView;
  range: TrendRange;
  trend: TrendPoint[];
  categoryShare: CategoryShare[];
}

export interface FxView {
  currency: string;
  amount: number;
  rate: number;
}

/**
 * 거래 한 줄.
 *
 * `amount`는 **원화 환산액**이다. 화면은 이 값만 쓴다 — `fx`는 「어떻게 그 값이 나왔나」를
 * 보여주는 보조 표기이고, FE가 다시 환산해 덮어쓰지 않는다(D-13).
 */
export interface TransactionView {
  id: number;
  type: LedgerFlow;
  status: TransactionStatus;
  occurredOn: string;
  occurredAt: string | null;
  amount: number;
  assetId: number;
  assetName: string | null;
  counterAssetId: number | null;
  counterAssetName: string | null;
  categoryId: number | null;
  categoryName: string | null;
  title: string | null;
  memo: string | null;
  source: TransactionSource;
  estimated: boolean;
  refundOfId: number | null;
  tags: string[];
  fx: FxView | null;
}

export interface MonthTotals {
  income: number;
  expense: number;
  /** 이체는 지출에도 수입에도 들어가지 않는다 — 따로 센다. */
  transfer: number;
  scheduledExpense: number;
  scheduledIncome: number;
  scheduledCount: number;
}

export interface DateGroup {
  date: string;
  income: number;
  expense: number;
  items: TransactionView[];
}

export interface TransactionListResponse {
  /** 오늘 기준선. **서버가 정한다** — 화면마다 계산하면 시간대가 갈릴 때 줄이 어긋난다. */
  todayLine: string;
  monthTotals: MonthTotals;
  groups: DateGroup[];
}

export interface AssetTransactionRow {
  transaction: TransactionView;
  /** 체크카드와 예정 줄은 `null`이다. */
  runningBalance: number | null;
}

export interface CategoryView {
  id: number;
  flow: LedgerFlow;
  name: string;
  parentId: number | null;
  color: string | null;
  icon: string | null;
  displayOrder: number;
  archived: boolean;
  children: CategoryView[];
}

export interface SettingsView {
  /** 1~28 또는 99(말일). */
  monthStartDay: number;
  monthStartWeekendPolicy: "AS_IS" | "PREV_BUSINESS_DAY";
  defaultAssetId: number | null;
  defaultPerspective: "SPEND" | "BILLING";
}

/** v1.5에서 채워질 값은 `null`이다 — `0`과 「아직 모른다」는 다르다. */
export interface LedgerSummary {
  monthEstimate: number;
  monthSpent: number;
  monthScheduled: number;
  uncategorizedCount: number;
  monthEndBalance: number | null;
  remainingOutflow: number | null;
  overdueCount: number | null;
  period: { start: string; end: string };
}

/** `rate`가 `null`이면 ECB에 닿지 못한 것이다. **에러가 아니다** — 직접 입력을 받는다. */
export interface FxRateResponse {
  currency: string;
  rate: number | null;
  referenceDate: string | null;
  source: string;
}

export interface SuggestionView {
  title: string;
  type: LedgerFlow;
  categoryId: number | null;
  categoryName: string | null;
  assetId: number;
  assetName: string | null;
  amount: number;
}

export interface FxInput {
  currency: string;
  amount: number;
  /** 비우면 서버가 ECB 고시로 채우고 **그 거래에 고정**한다. */
  rate?: number | null;
}

export interface TransactionCreateRequest {
  type: LedgerFlow;
  amount?: number | null;
  occurredOn: string;
  assetId?: number | null;
  counterAssetId?: number | null;
  categoryId?: number | null;
  title?: string | null;
  memo?: string | null;
  tags?: string[];
  fx?: FxInput | null;
}

export interface TransactionUpdateRequest {
  type?: LedgerFlow;
  amount?: number;
  occurredOn?: string;
  assetId?: number;
  counterAssetId?: number | null;
  categoryId?: number | null;
  clearCategory?: boolean;
  title?: string | null;
  memo?: string | null;
  tags?: string[];
  fx?: FxInput | null;
  clearFx?: boolean;
}

/** 미래 날짜를 보내면 요청과 달리 `SCHEDULED`로 저장된다 — 화면이 그 사실을 알린다. */
export interface TransactionCreatedResponse {
  transaction: TransactionView;
  savedAs: TransactionStatus;
}

export interface TransactionListParams {
  from?: string;
  to?: string;
}

export async function fetchAssets(): Promise<AssetListResponse> {
  const { data } =
    await client.get<ApiEnvelope<AssetListResponse>>("/ledger/assets");
  return data.data;
}

export async function fetchAssetDetail(
  id: number,
  range: TrendRange,
): Promise<AssetDetailResponse> {
  const { data } = await client.get<ApiEnvelope<AssetDetailResponse>>(
    `/ledger/assets/${id}`,
    { params: { range } },
  );
  return data.data;
}

export async function fetchAssetTransactions(
  id: number,
): Promise<AssetTransactionRow[]> {
  const { data } = await client.get<
    ApiEnvelope<{ items: AssetTransactionRow[] }>
  >(`/ledger/assets/${id}/transactions`);
  return data.data.items;
}

export interface AssetCreateRequest {
  name: string;
  type: AssetType;
  groupId?: number | null;
  accountLast4?: string | null;
  /** 체크카드면 **필수**다 — 없으면 서버가 `LDG-ERR-019`로 거부한다. */
  linkedAssetId?: number | null;
}

export async function createAsset(
  body: AssetCreateRequest,
): Promise<AssetView> {
  const { data } = await client.post<ApiEnvelope<AssetView>>(
    "/ledger/assets",
    body,
  );
  return data.data;
}

export interface AssetUpdateRequest {
  name?: string;
  groupId?: number | null;
  clearGroup?: boolean;
  accountLast4?: string | null;
  displayOrder?: number;
  hidden?: boolean;
  closedReason?: string | null;
  linkedAssetId?: number | null;
}

export async function updateAsset(
  id: number,
  body: AssetUpdateRequest,
): Promise<AssetView> {
  const { data } = await client.patch<ApiEnvelope<AssetView>>(
    `/ledger/assets/${id}`,
    body,
  );
  return data.data;
}

export async function fetchCategories(
  flow?: LedgerFlow,
): Promise<CategoryView[]> {
  const { data } = await client.get<ApiEnvelope<CategoryView[]>>(
    "/ledger/categories",
    { params: flow ? { flow } : undefined },
  );
  return data.data;
}

export async function fetchTransactions(
  params: TransactionListParams = {},
): Promise<TransactionListResponse> {
  const { data } = await client.get<ApiEnvelope<TransactionListResponse>>(
    "/ledger/transactions",
    { params },
  );
  return data.data;
}

export async function createTransaction(
  body: TransactionCreateRequest,
): Promise<TransactionCreatedResponse> {
  const { data } = await client.post<ApiEnvelope<TransactionCreatedResponse>>(
    "/ledger/transactions",
    body,
  );
  return data.data;
}

export async function updateTransaction(
  id: number,
  body: TransactionUpdateRequest,
): Promise<TransactionView> {
  const { data } = await client.patch<ApiEnvelope<TransactionView>>(
    `/ledger/transactions/${id}`,
    body,
  );
  return data.data;
}

/** 소프트 삭제. 되돌릴 수 있고, 환불은 이 API가 아니다. */
export async function deleteTransaction(id: number): Promise<void> {
  await client.delete(`/ledger/transactions/${id}`);
}

export async function fetchSuggestions(
  keyword: string,
): Promise<SuggestionView[]> {
  const { data } = await client.get<ApiEnvelope<SuggestionView[]>>(
    "/ledger/transactions/suggest",
    { params: { q: keyword } },
  );
  return data.data;
}

/**
 * 빠른 입력 템플릿(`LDG-013`). 날짜가 없다 — 템플릿으로 적는 건은 언제나 오늘이다.
 *
 * `useCount` 많은 순으로 온다. 순서를 사람이 관리하게 하지 않는다.
 */
export interface TemplateView {
  id: number;
  name: string;
  txType: LedgerFlow;
  amount: number;
  assetId: number;
  assetName: string | null;
  categoryId: number | null;
  categoryName: string | null;
  title: string | null;
  useCount: number;
}

export interface TemplateCreateRequest {
  name: string;
  txType: LedgerFlow;
  amount: number;
  assetId: number;
  categoryId?: number | null;
  title?: string | null;
}

export async function fetchTemplates(): Promise<TemplateView[]> {
  const { data } =
    await client.get<ApiEnvelope<TemplateView[]>>("/ledger/templates");
  return data.data;
}

export async function createTemplate(
  body: TemplateCreateRequest,
): Promise<TemplateView> {
  const { data } = await client.post<ApiEnvelope<TemplateView>>(
    "/ledger/templates",
    body,
  );
  return data.data;
}

export async function deleteTemplate(id: number): Promise<void> {
  await client.delete(`/ledger/templates/${id}`);
}

/** 한 번 눌러 오늘 날짜로 기록한다. 쓸 때마다 순위가 오른다. */
export async function applyTemplate(
  id: number,
): Promise<TransactionCreatedResponse> {
  const { data } = await client.post<ApiEnvelope<TransactionCreatedResponse>>(
    `/ledger/templates/${id}/apply`,
  );
  return data.data;
}

/** 내역 복사(`LDG-014`). 기본은 오늘 날짜다. */
export async function duplicateTransaction(
  id: number,
  useToday: boolean,
): Promise<TransactionCreatedResponse> {
  const { data } = await client.post<ApiEnvelope<TransactionCreatedResponse>>(
    `/ledger/transactions/${id}/duplicate`,
    { useToday },
  );
  return data.data;
}

/**
 * 다건 입력(`LDG-015`) 결과.
 *
 * 서버가 **한 트랜잭션**으로 처리한다 — 「7건 성공 3건 실패」 같은 응답이 없다.
 */
export interface BulkCreateResponse {
  created: TransactionView[];
  scheduledCount: number;
}

export async function bulkCreateTransactions(
  transactions: TransactionCreateRequest[],
): Promise<BulkCreateResponse> {
  const { data } = await client.post<ApiEnvelope<BulkCreateResponse>>(
    "/ledger/transactions/bulk-create",
    { transactions },
  );
  return data.data;
}

export interface ReceiptView {
  id: number;
  objectKey: string;
  url: string;
  contentType: string | null;
  byteSize: number | null;
  displayOrder: number;
}

export interface ReceiptUploadUrl {
  uploadUrl: string;
  publicUrl: string;
  objectKey: string;
}

export async function createReceiptUploadUrl(
  contentType: string,
): Promise<ReceiptUploadUrl> {
  const { data } = await client.post<ApiEnvelope<ReceiptUploadUrl>>(
    "/ledger/receipts/upload-url",
    { contentType },
  );
  return data.data;
}

export async function fetchReceipts(
  transactionId: number,
): Promise<ReceiptView[]> {
  const { data } = await client.get<ApiEnvelope<ReceiptView[]>>(
    `/ledger/transactions/${transactionId}/receipts`,
  );
  return data.data;
}

export async function attachReceipt(
  transactionId: number,
  body: { objectKey: string; contentType?: string; byteSize?: number },
): Promise<ReceiptView> {
  const { data } = await client.post<ApiEnvelope<ReceiptView>>(
    `/ledger/transactions/${transactionId}/receipts`,
    body,
  );
  return data.data;
}

/** 첨부를 뗀다. **오브젝트는 남는다** — 되돌릴 수 있어야 한다. */
export async function detachReceipt(id: number): Promise<void> {
  await client.delete(`/ledger/receipts/${id}`);
}

export async function fetchSettings(): Promise<SettingsView> {
  const { data } =
    await client.get<ApiEnvelope<SettingsView>>("/ledger/settings");
  return data.data;
}

export interface SettingsUpdateRequest {
  monthStartDay?: number;
  monthStartWeekendPolicy?: "AS_IS" | "PREV_BUSINESS_DAY";
  defaultAssetId?: number | null;
  clearDefaultAsset?: boolean;
  defaultPerspective?: "SPEND" | "BILLING";
}

export async function updateSettings(
  body: SettingsUpdateRequest,
): Promise<SettingsView> {
  const { data } = await client.patch<ApiEnvelope<SettingsView>>(
    "/ledger/settings",
    body,
  );
  return data.data;
}

/**
 * 대시보드. **v1은 세 값뿐이다** — 이미 쓴 돈 · 이번 달 수입 · 정리할 내역.
 *
 * 2축 요약·미납·다가오는 결제는 **필드 자체가 없다**(D-7). 서버가 안 내리는 것을 화면이
 * 빈 카드로 그리면 고장난 것처럼 보인다.
 */
export interface LedgerDashboard {
  spending: { spent: number };
  income: { amount: number };
  todo: { uncategorized: number };
  period: { start: string; end: string; monthStartDay: number };
}

export async function fetchDashboard(): Promise<LedgerDashboard> {
  const { data } =
    await client.get<ApiEnvelope<LedgerDashboard>>("/ledger/dashboard");
  return data.data;
}

export interface CategoryStat {
  /** `null`이면 미분류. */
  categoryId: number | null;
  categoryName: string | null;
  amount: number;
  count: number;
  /** 0~1. 서버가 계산해 준다 — 화면이 다시 나누지 않는다. */
  share: number;
}

export interface StatsComparisonBucket {
  start: string;
  end: string;
  total: number;
  /** 이번 − 그때. 양수면 더 썼다. */
  diff: number;
}

export interface LedgerStats {
  period: { start: string; end: string; label: string };
  total: number;
  byCategory: CategoryStat[];
  comparison: {
    previousPeriod: StatsComparisonBucket;
    previousYear: StatsComparisonBucket;
  };
}

/** `period`는 `YYYY-MM`. 생략하면 지금 속한 구간이다. */
export async function fetchStats(period?: string): Promise<LedgerStats> {
  const { data } = await client.get<ApiEnvelope<LedgerStats>>("/ledger/stats", {
    params: period ? { period } : undefined,
  });
  return data.data;
}

export interface ReconcileRequest {
  actualBalance: number;
  occurredOn?: string;
  memo?: string | null;
}

export interface ReconcileResponse {
  /** 차이가 0이면 `null` — 없는 거래를 만들지 않는다. */
  adjustmentTransactionId: number | null;
  difference: number;
  balanceAfter: number;
}

export async function reconcileAsset(
  id: number,
  body: ReconcileRequest,
): Promise<ReconcileResponse> {
  const { data } = await client.post<ApiEnvelope<ReconcileResponse>>(
    `/ledger/assets/${id}/reconcile`,
    body,
  );
  return data.data;
}

export async function fetchLedgerSummary(): Promise<LedgerSummary> {
  const { data } =
    await client.get<ApiEnvelope<LedgerSummary>>("/ledger/summary");
  return data.data;
}

/**
 * 환율 조회. 실패해도 예외가 아니라 `rate: null`이 온다 —
 * 화면은 그때 직접 입력 칸을 열고 저장을 막지 않는다.
 */
export async function fetchFxRate(
  currency: string,
  on?: string,
): Promise<FxRateResponse> {
  const { data } = await client.get<ApiEnvelope<FxRateResponse>>(
    "/ledger/fx/rate",
    { params: { currency, ...(on ? { on } : {}) } },
  );
  return data.data;
}
