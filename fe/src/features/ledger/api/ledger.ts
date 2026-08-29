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

/**
 * 예정의 네 출처(확정 명세 §8.1). **한 테이블에 있지 않다** — 직접 예약만 실체화돼 있고
 * 나머지 셋은 규칙·청구서·할부에서 파생된다.
 *
 * 종류를 내려주는 이유는 배지 색만이 아니다. **같은 돈이 두 번 세어지지 않았음**을 사람이
 * 눈으로 확인할 수 있어야 한다.
 */
export type UpcomingKind =
  | "RECURRING"
  | "ONE_OFF"
  | "CARD_PAYMENT"
  | "INSTALLMENT";

export interface UpcomingItem {
  kind: UpcomingKind;
  date: string;
  /** 음수면 이미 지났다는 뜻이고, 그건 미납이다. */
  dday: number;
  title: string | null;
  amount: number;
  flow: LedgerFlow;
  /** 소비가 아니다 — 카드 대금·계좌 이체에 배지를 하나 더 단다. */
  isTransfer: boolean;
  /** 미납. **확정하거나 건너뛰어야만 사라진다** — 「무시」는 없다. */
  overdue: boolean;
  estimated: boolean;
  categoryId: number | null;
  assetId: number | null;
  assetName: string | null;
  transactionId: number | null;
  recurringId: number | null;
  /** 규칙이 계산한 **원래** 예정일. 회차를 조작할 때의 키다. */
  occurrenceDate: string | null;
  statementId: number | null;
  installmentId: number | null;
}

/**
 * 잔액이 가장 낮아지는 지점.
 *
 * 「월말에 얼마 남나」보다 **「중간에 모자라지 않나」**가 먼저다 — 25일에 청약이 빠지고 나면
 * 바닥인데 월말 숫자만 보면 괜찮아 보인다.
 */
export interface MinBalance {
  amount: number;
  date: string;
  reason: string | null;
}

export interface UpcomingStats {
  /** 나갈 돈. **지출과 이체를 함께** 센다. */
  outflow: number;
  income: number;
  currentBalance: number;
  expectedBalance: number;
  minBalance: MinBalance;
  count: number;
  byKind: Partial<Record<UpcomingKind, number>>;
}

export interface UpcomingResponse {
  from: string;
  to: string;
  days: number;
  stats: UpcomingStats;
  items: UpcomingItem[];
}

/**
 * 캘린더 하루(`LDG-021`).
 *
 * **과거는 확정, 미래는 예정**을 따로 담는다 — 한 칸에 합치면 화면이 연하게 그릴 수 없고
 * 「이미 쓴 돈」과 「나갈 예정인 돈」이 같은 굵기로 보인다.
 */
export interface CalendarDay {
  date: string;
  income: number;
  expense: number;
  scheduledIncome: number;
  scheduledExpense: number;
  scheduledTransfer: number;
}

export interface CalendarResponse {
  month: string;
  todayLine: string;
  days: CalendarDay[];
}

/** 회차 조작. **「무시」에 해당하는 값이 없다** — 의도적으로 넣지 않았다(§6.4). */
export type OccurrenceAction =
  | "AMOUNT"
  | "SKIP"
  | "MOVE"
  | "UNPAID"
  | "REVERTED";

export interface OccurrenceRequest {
  recurringId: number;
  /** 규칙이 계산한 **원래** 예정일. 날짜를 옮겨도 이 값이 키다. */
  occurrenceDate: string;
  action: OccurrenceAction;
  amount?: number | null;
  movedTo?: string | null;
  note?: string | null;
}

export interface OccurrenceConfirmRequest {
  recurringId: number;
  occurrenceDate: string;
  /** 실제로 빠진 날. 새 거래를 만들지 않고 그 회차를 되살려 옮긴다. */
  actualDate: string;
  amount?: number | null;
}

export interface OccurrenceView {
  recurringId: number;
  name: string;
  occurrenceDate: string;
  date: string;
  amount: number;
  action: OccurrenceAction;
  overdue: boolean;
  transactionId: number | null;
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

/**
 * v1에서는 뒤의 셋이 `null`이었다 — `0`과 「아직 모른다」는 다르다. v1.5에서 카드 청구서와
 * 정기 항목이 생겨 채워진다. 타입은 `null`을 남긴다: 값이 없는 상태가 다시 생길 수 있고,
 * 그때 `0`으로 둘러대면 화면이 「없음」을 그린다.
 */
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
 * 대시보드.
 *
 * **`spending`과 `cashflow`를 한 덩어리로 합치지 않는다**(확정 명세 §8.2). 다른 질문에
 * 답한다 — 앞은 「이번 달 얼마 쓰나」(소비 시점, 카드 대금 제외), 뒤는 「통장에서 얼마
 * 빠지나」(출금 시점, 이체와 카드 대금 포함)다. 한 숫자로 합치면 카드로 쓴 돈이 두 번 세어진다.
 */
export interface LedgerDashboard {
  spending: { spent: number; scheduled: number; estimate: number };
  cashflow: {
    /** 지금 쓸 수 있는 돈. **저축은 빠져 있다** — 옮긴 돈은 이번 달 쓸 돈이 아니다. */
    balance: number;
    remainingOutflow: number;
    remainingInflow: number;
    monthEndBalance: number;
    minBalance: MinBalance;
  };
  income: { amount: number };
  netWorth: { totalAssets: number; liabilities: number; netWorth: number };
  /** 다가오는 결제 5건. 더 보려면 `/ledger/upcoming`으로 간다. */
  upcoming: UpcomingItem[];
  todo: { uncategorized: number; overdue: number };
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

/**
 * 예산. **게이지는 2단이다** — `spent`가 진한 부분, `scheduled`가 연한 부분(§8.2).
 *
 * `period`를 안 세운 달도 응답이 온다(`totalAmount: 0`). 「예산이 없다」가 조회 실패일 이유가 없다.
 */
export interface BudgetCategoryProgress {
  /** `null`이면 미분류. */
  categoryId: number | null;
  name: string;
  amount: number;
  spent: number;
  scheduled: number;
}

export interface BudgetResponse {
  period: string;
  periodStart: string;
  periodEnd: string;
  totalAmount: number;
  /** 정기 항목 월 환산 합. 미리 빼 「쓸 수 있는 돈」만 남긴다. */
  fixedCostTotal: number;
  spendable: number;
  spent: number;
  scheduled: number;
  remaining: number;
  daysLeft: number;
  dailyAllowance: number;
  categories: BudgetCategoryProgress[];
}

/** 청구서의 네 상태. **「미납」이 여기 없다** — 미납은 상태가 아니라 판정이다(D-8). */
export type StatementStatus = "COLLECTING" | "CONFIRMED" | "PARTIAL" | "PAID";

/**
 * 청구액 산식(확정 명세 §7.4).
 *
 * ```
 * 청구액 = 사용 + 할부 회차 + 이월 + 이자·수수료 + 차액 − 환불 − 할인
 * ```
 *
 * **합계만 주면 카드사 앱과 다를 때 어디가 다른지 알 방법이 없다.** 화면은 이 일곱 항목을
 * 그대로 그린다 — 다시 계산하지 않는다.
 */
export interface StatementBreakdown {
  usage: number;
  installment: number;
  carriedOver: number;
  interestFee: number;
  adjustment: number;
  refund: number;
  discount: number;
  billed: number;
  paid: number;
  remaining: number;
}

export interface StatementView {
  id: number;
  cardAssetId: number;
  cycleStart: string;
  cycleEnd: string;
  paymentDate: string;
  status: StatementStatus;
  /** 결제일이 지났는데 아직 안 냈다. **저장값이 아니라 판정**이다. */
  overdue: boolean;
  breakdown: StatementBreakdown;
  paidOn: string | null;
  carriedToStatementId: number | null;
}

export interface CardView {
  id: number;
  name: string;
  accountLast4: string | null;
  cycleStartDay: number | null;
  cycleCloseDay: number | null;
  paymentDay: number | null;
  paymentAssetId: number | null;
  paymentAssetName: string | null;
  creditLimit: number | null;
  /** 사이클이 없으면 청구서가 만들어지지 않는다. 오류가 아니라 「아직 등록 안 함」이다. */
  hasCycle: boolean;
  /** 미결제 사용액. **잔액이 아니라 부채**다. */
  unpaidAmount: number;
  currentStatement: StatementView | null;
}

export interface CardListResponse {
  cards: CardView[];
  /** 할부 잔여 원금 합계. 청구 여부와 무관하게 이미 갚기로 한 돈이다. */
  installmentOutstanding: number;
}

export interface StatementPayRequest {
  /** 비우면 **남은 전액**. 일부만 내면 잔액이 다음 청구서로 이월된다. */
  amount?: number | null;
  paymentAssetId?: number | null;
  /** **실제 출금일**. 비우면 청구서의 결제일이지만, 다르면 실제가 맞다. */
  paidOn?: string | null;
}

export async function fetchCards(): Promise<CardListResponse> {
  const { data } =
    await client.get<ApiEnvelope<CardListResponse>>("/ledger/cards");
  return data.data;
}

export async function fetchStatements(
  cardId: number,
): Promise<StatementView[]> {
  const { data } = await client.get<ApiEnvelope<StatementView[]>>(
    `/ledger/cards/${cardId}/statements`,
  );
  return data.data;
}

export async function fetchStatementTransactions(
  statementId: number,
): Promise<TransactionView[]> {
  const { data } = await client.get<ApiEnvelope<TransactionView[]>>(
    `/ledger/statements/${statementId}/transactions`,
  );
  return data.data;
}

/** 결제 처리. **자동으로 적지 않는다** — 실제 출금액을 앱이 알 수 없다(§7.2). */
export async function payStatement(
  statementId: number,
  body: StatementPayRequest,
): Promise<StatementView> {
  const { data } = await client.post<ApiEnvelope<StatementView>>(
    `/ledger/statements/${statementId}/pay`,
    body,
  );
  return data.data;
}

/** 정기 항목의 종류. **표시·필터용 라벨일 뿐** — 동작은 전부 같다(§6.1). */
export type RecurringKind =
  | "SUBSCRIPTION"
  | "FIXED_COST"
  | "INSURANCE"
  | "TRANSFER"
  | "INCOME";

export type RecurringStatus = "ACTIVE" | "PAUSED" | "ENDED";

export type FrequencyType =
  | "WEEKLY"
  | "MONTHLY_DAY"
  | "MONTHLY_LAST"
  | "EVERY_N_MONTHS"
  | "YEARLY"
  | "EVERY_N_DAYS";

export interface RecurringView {
  id: number;
  name: string;
  kind: RecurringKind;
  txType: LedgerFlow;
  amount: number;
  /** `VARIABLE`이면 예상액이다 — 화면이 `~152,000`으로 적는다. */
  amountType: "FIXED" | "VARIABLE";
  assetId: number;
  assetName: string | null;
  counterAssetId: number | null;
  categoryId: number | null;
  categoryName: string | null;
  freqType: FrequencyType;
  freqInterval: number | null;
  freqDay: number | null;
  freqMonth: number | null;
  /** `매월 25일`. **서버가 만든다** — 화면마다 조립하면 같은 규칙이 다르게 읽힌다. */
  freqLabel: string;
  businessDayPolicy: "AS_IS" | "PREV" | "NEXT";
  startDate: string;
  endDate: string | null;
  pausedFrom: string | null;
  pausedTo: string | null;
  status: RecurringStatus;
  endedOn: string | null;
  cancelUrl: string | null;
  memo: string | null;
  nextDate: string | null;
  /** 월 환산액. 연간 구독은 ÷12다. */
  monthlyEquivalent: number;
}

/** 점검 신호 4종(§6.6). 「이거 아직도 내고 있었나」를 찾아내는 것이 목적이다. */
export interface RecurringSignals {
  priceIncreased: {
    recurringId: number;
    name: string;
    from: number;
    to: number;
    changedOn: string;
  }[];
  trialEnding: {
    recurringId: number;
    name: string;
    endsOn: string;
    amount: number;
  }[];
  longUnchanged: number[];
  noEndDate: number[];
}

export interface RecurringOverdue {
  recurringId: number;
  name: string;
  occurrenceDate: string;
  amount: number;
  daysOverdue: number;
  note: string | null;
}

export interface RecurringListResponse {
  items: RecurringView[];
  stats: {
    monthlyFixedTotal: number;
    yearlyTotal: number;
    subscriptionCount: number;
    activeCount: number;
  };
  signals: RecurringSignals;
  overdue: RecurringOverdue[];
}

export interface RecurringHistoryResponse {
  amounts: {
    effectiveFrom: string;
    amount: number;
    changeFromAmount: number | null;
  }[];
  /** 건너뛰기·되돌리기·미납. **몇 달째 되돌리고 있는지**가 여기서 보인다. */
  missed: {
    occurrenceDate: string;
    action: OccurrenceAction;
    note: string | null;
  }[];
}

/**
 * 해지. `revertPostedAfter`에 **기본값이 없다** — 이미 원장에 들어간 것을 되돌리는 유일한
 * 경로라 사람이 매번 답해야 한다.
 */
export interface RecurringEndRequest {
  endedOn: string;
  revertPostedAfter: boolean;
}

export async function fetchRecurring(): Promise<RecurringListResponse> {
  const { data } =
    await client.get<ApiEnvelope<RecurringListResponse>>("/ledger/recurring");
  return data.data;
}

export async function fetchRecurringHistory(
  id: number,
): Promise<RecurringHistoryResponse> {
  const { data } = await client.get<ApiEnvelope<RecurringHistoryResponse>>(
    `/ledger/recurring/${id}/history`,
  );
  return data.data;
}

export async function pauseRecurring(
  id: number,
  body: { from: string; to?: string | null },
): Promise<RecurringView> {
  const { data } = await client.post<ApiEnvelope<RecurringView>>(
    `/ledger/recurring/${id}/pause`,
    body,
  );
  return data.data;
}

export async function resumeRecurring(id: number): Promise<RecurringView> {
  const { data } = await client.post<ApiEnvelope<RecurringView>>(
    `/ledger/recurring/${id}/resume`,
  );
  return data.data;
}

export async function endRecurring(
  id: number,
  body: RecurringEndRequest,
): Promise<{ reverted: number; message: string }> {
  const { data } = await client.post<
    ApiEnvelope<{ reverted: number; message: string }>
  >(`/ledger/recurring/${id}/end`, body);
  return data.data;
}

export interface BudgetPutRequest {
  totalAmount: number;
  categories?: { categoryId: number; amount: number }[];
}

/** **통째로 갈아 끼운다** — 보낸 카테고리 목록이 곧 그 달의 전부다. */
export async function putBudget(
  period: string,
  body: BudgetPutRequest,
): Promise<BudgetResponse> {
  const { data } = await client.put<ApiEnvelope<BudgetResponse>>(
    "/ledger/budget",
    body,
    { params: { period } },
  );
  return data.data;
}

export async function fetchBudget(period?: string): Promise<BudgetResponse> {
  const { data } = await client.get<ApiEnvelope<BudgetResponse>>(
    "/ledger/budget",
    { params: period ? { period } : undefined },
  );
  return data.data;
}

/** 예정 목록. 기본 30일, 최대 12개월 — 그 너머는 예정이 아니라 추측이다. */
export async function fetchUpcoming(days = 30): Promise<UpcomingResponse> {
  const { data } = await client.get<ApiEnvelope<UpcomingResponse>>(
    "/ledger/upcoming",
    { params: { days } },
  );
  return data.data;
}

/** `month`는 `YYYY-MM`. 생략하면 이번 달이다. */
export async function fetchCalendar(month?: string): Promise<CalendarResponse> {
  const { data } = await client.get<ApiEnvelope<CalendarResponse>>(
    "/ledger/transactions/calendar",
    { params: month ? { month } : undefined },
  );
  return data.data;
}

/** 회차 하나를 손댄다 — 금액·건너뛰기·날짜·미납·되돌리기. */
export async function patchOccurrence(
  body: OccurrenceRequest,
): Promise<OccurrenceView> {
  const { data } = await client.patch<ApiEnvelope<OccurrenceView>>(
    "/ledger/upcoming/occurrence",
    body,
  );
  return data.data;
}

/** 미납을 **실제 출금일로** 확정한다. */
export async function confirmOccurrence(
  body: OccurrenceConfirmRequest,
): Promise<OccurrenceView> {
  const { data } = await client.post<ApiEnvelope<OccurrenceView>>(
    "/ledger/upcoming/occurrence/confirm",
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
