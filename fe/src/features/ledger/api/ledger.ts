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

/** 삭제를 막는 것. 화면은 이 이름으로 「무엇을 치워야 하는지」를 적는다. */
export type DeleteBlocker =
  | "TRANSACTION"
  | "DELETED_TRANSACTION"
  | "RECURRING"
  | "TEMPLATE"
  | "LINKED_ASSET";

export interface AssetDetailResponse {
  /**
   * 지울 수 있는가. 눌러 보고 알게 하지 않으려고 서버가 미리 판정해 준다 —
   * 청구서는 세지 않는다(사이클 자리표라 자산과 함께 지워진다).
   */
  deletable: boolean;
  /** 지울 수 없다면 무엇 때문인지. 「안 됩니다」만으로는 무엇을 치울지 알 수 없다. */
  deleteBlockers: DeleteBlocker[];
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

/** 고정비인가 변동비인가(`LDG-061`). `null`이면 아직 정하지 않았다. */
export type LedgerCostType = "FIXED" | "VARIABLE";

export interface CategoryView {
  id: number;
  flow: LedgerFlow;
  name: string;
  parentId: number | null;
  color: string | null;
  icon: string | null;
  displayOrder: number;
  archived: boolean;
  /** `null`이면 아직 정하지 않았다 — 「모른다」와 「변동비다」는 다르다. */
  costType: LedgerCostType | null;
  /** 카드 실적에서 뺀다. 세금·보험료처럼 카드사가 안 세는 것들. */
  excludeFromCardGoal: boolean;
  /** 연간 결산에서 뺀다. 저축·투자처럼 「쓴 돈」이 아니라 자산 이동인 것들. */
  excludeFromSettlement: boolean;
  children: CategoryView[];
}

export interface CategoryAttributesRequest {
  costType?: LedgerCostType | null;
  clearCostType?: boolean;
  excludeFromCardGoal?: boolean;
  excludeFromSettlement?: boolean;
}

export async function updateCategoryAttributes(
  id: number,
  body: CategoryAttributesRequest,
): Promise<CategoryView> {
  const { data } = await client.patch<ApiEnvelope<CategoryView>>(
    `/ledger/categories/${id}`,
    body,
  );
  return data.data;
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

/**
 * 자산 삭제. <b>아직 아무것도 붙지 않은 자산에만</b> 열린다 — 거래·정기 항목·청구서가
 * 하나라도 붙었으면 서버가 `LDG-ERR-034`로 거부한다. 그때 사람이 원하는 것은 해지다.
 */
export async function deleteAsset(id: number): Promise<void> {
  await client.delete(`/ledger/assets/${id}`);
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

/**
 * 소비 기준 / 청구 기준(`LDG-086` · 확정 명세 §10.1).
 *
 * **청구서·예정·예상 잔액 화면은 이 값과 무관하게 항상 청구 기준이다.** 그 API들은
 * `perspective` 파라미터를 아예 받지 않는다.
 */
export type LedgerPerspective = "SPEND" | "BILLING";

export interface AssetStat {
  assetId: number;
  assetName: string | null;
  amount: number;
  share: number;
}

/**
 * 고정 대 변동.
 *
 * `unclassified`는 **속성을 아직 안 정한 카테고리**의 지출이다. 변동비에 몰아넣지 않는다 —
 * 그러면 아무도 분류하지 않은 가계부에서 「변동비가 100%」라는 거짓말이 나온다.
 */
export interface FixedVsVariable {
  fixed: number;
  variable: number;
  unclassified: number;
}

export interface MonthlyPoint {
  month: string;
  expense: number;
  income: number;
  fixed: number;
  variable: number;
  /**
   * 속성을 안 정한 카테고리의 지출. **셋을 더해야 `expense`가 된다** —
   * 빼고 그리면 막대가 그 달 지출보다 짧아지고, 왜 짧은지는 화면에 안 나온다.
   */
  unclassified: number;
  /** 아직 오지 않은 달은 `null`이다. */
  netWorth: number | null;
}

export interface LedgerSettlement {
  year: number;
  income: number;
  expense: number;
  /** 수입이 없으면 `null` — 0은 「하나도 못 모았다」로 읽히는데 사실은 「셀 수 없다」다. */
  savingRate: number | null;
  highestMonth: string | null;
  lowestMonth: string | null;
}

/**
 * 다른 관점으로 보면 얼마가 달라지는가. **서버가 계산해 준다** — 화면이 다시 세면
 * 어느 쪽이 맞는지 알 수 없다(D-13).
 *
 * `reason`은 벌어지지 않으면 `null`이다. 원인이 둘이면 둘 다 말한다.
 */
export interface PerspectiveDiff {
  other: LedgerPerspective;
  otherTotal: number;
  diff: number;
  reason: string | null;
}

export interface LedgerStats {
  period: { start: string; end: string; label: string };
  perspective: LedgerPerspective;
  total: number;
  byCategory: CategoryStat[];
  byAsset: AssetStat[];
  fixedVsVariable: FixedVsVariable;
  monthly: MonthlyPoint[];
  settlement: LedgerSettlement;
  comparison: {
    previousPeriod: StatsComparisonBucket;
    previousYear: StatsComparisonBucket;
  };
  perspectiveDiff: PerspectiveDiff;
}

/** `period`는 `YYYY-MM`. 생략하면 지금 속한 구간, 관점을 생략하면 설정의 기본값이다. */
export async function fetchStats(
  period?: string,
  perspective?: LedgerPerspective,
): Promise<LedgerStats> {
  const { data } = await client.get<ApiEnvelope<LedgerStats>>("/ledger/stats", {
    params: {
      ...(period ? { period } : {}),
      ...(perspective ? { perspective } : {}),
    },
  });
  return data.data;
}

export interface SearchRequest {
  from: string;
  to: string;
  type?: LedgerFlow | null;
  assetId?: number | null;
  categoryId?: number | null;
  minAmount?: number | null;
  maxAmount?: number | null;
  keyword?: string | null;
}

export interface SearchResponse {
  items: TransactionView[];
  count: number;
  total: number;
  /** 상한에 걸려 잘렸는가. **숨기지 않는다** — 모르고 일괄 편집하면 일부만 바뀐다. */
  truncated: boolean;
}

export async function searchTransactions(
  body: SearchRequest,
): Promise<SearchResponse> {
  const { data } = await client.post<ApiEnvelope<SearchResponse>>(
    "/ledger/stats/search",
    body,
  );
  return data.data;
}

/** 예상 잔액 곡선(§8.4). **관점 파라미터가 없다** — 언제나 청구 기준이다. */
export interface BalanceCurvePoint {
  date: string;
  delta: number;
  balance: number;
}

export interface BalanceCurve {
  from: string;
  to: string;
  currentBalance: number;
  points: BalanceCurvePoint[];
  minBalance: MinBalance;
  /** 잔액이 처음 마이너스가 되는 날. 없으면 `null` — 0으로 두면 오늘이 된다. */
  firstNegativeDate: string | null;
}

export async function fetchBalanceCurve(days = 30): Promise<BalanceCurve> {
  const { data } = await client.get<ApiEnvelope<BalanceCurve>>(
    "/ledger/upcoming/balance-curve",
    { params: { days } },
  );
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

/** 실적 집계 기준. **카드 속성이지 전역 설정이 아니다**(§7.6). */
export type UsageGoalBasis = "APPROVAL" | "BILLING";

export interface UsageGoalView {
  goalAmount: number;
  basis: UsageGoalBasis;
  counted: number;
  /** 조건까지 남은 금액. 「88,000원 더 쓰면 충족」이 이 값이다. */
  remaining: number;
  achieved: boolean;
  month: string;
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
  /** 실적을 안 걸어 둔 카드는 `null`이다 — 0%로 그리면 「못 채웠다」로 읽힌다. */
  usageGoal: UsageGoalView | null;
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

export interface UsageGoalRequest {
  /** `null`이면 조건을 지운다 — 0으로 두면 「0원만 채우면 된다」가 되어 언제나 달성이다. */
  goalAmount: number | null;
  basis: UsageGoalBasis | null;
}

export async function updateUsageGoal(
  cardId: number,
  body: UsageGoalRequest,
): Promise<void> {
  await client.patch(`/ledger/cards/${cardId}/usage-goal`, body);
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

/* ─── 이관 (#1268 · 확정 명세 §12) ─────────────────────────────────── */

/**
 * 열 매핑. 값은 **0부터 세는 열 번호**다.
 *
 * 이름이 아니라 번호인 이유는 머리글이 없는 파일이 있어서다 — 이름을 키로 삼으면
 * 그런 파일은 아예 매핑할 수 없다.
 */
export interface ImportMapping {
  date: number | null;
  amount?: number | null;
  /** 입금 열. 은행 내역은 입금·출금이 **두 열**로 나뉘어 온다. */
  inflow?: number | null;
  outflow?: number | null;
  title?: number | null;
  memo?: number | null;
  type?: number | null;
  category?: number | null;
  /** 자산 열. 백업을 되돌릴 때 계좌가 한 덩이로 뭉치지 않게 한다. */
  asset?: number | null;
}

export interface ImportPreset {
  id: number;
  name: string;
  mapping: ImportMapping;
  skipRows: number;
  dateFormat: string | null;
  /** 동봉 프리셋. 고칠 수도 지울 수도 없다. */
  builtIn: boolean;
}

export interface ImportAnalyzeResponse {
  headers: string[];
  sample: string[][];
  totalRows: number;
  /**
   * 머리글이 몇 번째 줄이었는지(0부터). 은행 파일은 앞에 안내문이 붙어 와서 1행이 아니다 —
   * 화면의 「건너뛸 머리글 줄 수」는 이 값 + 1로 채운다.
   */
  headerRow: number;
  presets: ImportPreset[];
}

/**
 * 미리보기 한 줄.
 *
 * `duplicateOf`는 **보여줄 뿐**이다(`LDG-092`) — 자동으로 합치지 않고, 병합 API도 없다.
 * 사람이 실행 목록에서 그 줄을 빼는 것이 유일한 처리다.
 */
export interface ImportPreviewRow {
  rowNumber: number;
  occurredOn: string | null;
  type: LedgerFlow | null;
  amount: number | null;
  title: string | null;
  memo: string | null;
  categoryId: number | null;
  categoryName: string | null;
  /** 형식 오류 사유. 있으면 이 줄은 넣을 수 없다. */
  error: string | null;
  duplicateOf: number | null;
  /**
   * 같아 보이는 **앞 파일의 줄**. 기간이 겹치게 내려받은 파일을 함께 올렸을 때 걸린다 —
   * 아직 원장에 없어서 id가 없으므로 자리로 가리킨다. `duplicateOf`가 있으면 비어 있다.
   */
  duplicateOfRow: ImportRowRef | null;
  assetId: number | null;
  assetName: string | null;
}

export interface ImportRowRef {
  fileIndex: number;
  rowNumber: number;
}

/**
 * 파일 한 장의 미리보기.
 *
 * 줄 번호는 **파일 안에서** 세므로 합쳐 놓으면 3번 줄이 여러 개가 된다 — 그래서 파일
 * 경계를 살려 받는다.
 */
export interface ImportFilePreview {
  fileIndex: number;
  fileName: string | null;
  rows: ImportPreviewRow[];
  totalRows: number;
  duplicateCount: number;
  errorCount: number;
}

export interface ImportPreviewResponse {
  files: ImportFilePreview[];
  totalRows: number;
  /** 합계. 서버가 센다 — 화면이 다시 세지 않는다. */
  duplicateCount: number;
  errorCount: number;
}

/** 파일 한 장을 어떻게 읽을지. 파일마다 따로 온다 — 은행 파일과 카드 명세서는 열이 다르다. */
export interface ImportFileMapping {
  assetId: number;
  mapping: ImportMapping;
  skipRows?: number;
  dateFormat?: string | null;
  password?: string;
}

/** 파일 한 장이 만든 배치. 되돌리기가 **파일 단위**로 남는다. */
export interface ImportBatchResult {
  batchId: number;
  fileName: string | null;
  inserted: number;
  skipped: number;
}

export interface ImportExecuteResponse {
  batches: ImportBatchResult[];
  /** 전체 합계. 파일별 내역은 `batches`에 있다. */
  inserted: number;
  skipped: number;
}

export interface ImportBatch {
  id: number;
  source: string;
  fileName: string | null;
  rowCount: number;
  insertedCount: number;
  createdAt: string;
  /** 되돌린 시각. 되돌린 배치도 목록에 남는다 — 그것도 이력이다. */
  revertedAt: string | null;
}

/**
 * @param password 암호가 걸린 xlsx의 비밀번호. 은행 거래내역이 그렇게 내려온다 —
 *                 서버는 그 요청에서만 쓰고 저장하지 않는다
 */
export async function analyzeImport(
  file: File,
  password?: string,
): Promise<ImportAnalyzeResponse> {
  const form = new FormData();
  form.append("file", file);
  if (password) {
    form.append("password", password);
  }
  const { data } = await client.post<ApiEnvelope<ImportAnalyzeResponse>>(
    "/ledger/import/analyze",
    form,
  );
  return data.data;
}

/** 요청 본문을 JSON 파트로 붙인다 — 서버가 `@RequestPart`로 받는다. */
function jsonPart(body: unknown): Blob {
  return new Blob([JSON.stringify(body)], { type: "application/json" });
}

/**
 * 파일과 설정을 **순서로 짝지어** 보낸다.
 *
 * 한 요청에 다 보내는 것이 파일끼리 겹치는 줄을 보기 위한 조건이다 — 파일마다 따로
 * 물으면 두 번째 파일을 볼 때 첫 파일은 아직 원장에도 없어서 중복으로 걸리지 않는다.
 */
export async function previewImport(
  files: File[],
  fileRequests: ImportFileMapping[],
): Promise<ImportPreviewResponse> {
  const form = new FormData();
  files.forEach((file) => form.append("files", file));
  form.append("request", jsonPart({ files: fileRequests }));
  const { data } = await client.post<ApiEnvelope<ImportPreviewResponse>>(
    "/ledger/import/preview",
    form,
  );
  return data.data;
}

export async function executeImport(
  files: File[],
  fileRequests: (ImportFileMapping & {
    source: string;
    /** 넣을 줄 번호. 체크를 해제한 줄은 여기 없다. */
    rowNumbers: number[];
  })[],
): Promise<ImportExecuteResponse> {
  const form = new FormData();
  files.forEach((file) => form.append("files", file));
  form.append("request", jsonPart({ files: fileRequests }));
  const { data } = await client.post<ApiEnvelope<ImportExecuteResponse>>(
    "/ledger/import/execute",
    form,
  );
  return data.data;
}

export async function fetchImportBatches(): Promise<ImportBatch[]> {
  const { data } = await client.get<ApiEnvelope<ImportBatch[]>>(
    "/ledger/import/batches",
  );
  return data.data;
}

export async function revertImportBatch(
  id: number,
): Promise<{ batchId: number; reverted: number }> {
  const { data } = await client.post<
    ApiEnvelope<{ batchId: number; reverted: number }>
  >(`/ledger/import/batches/${id}/revert`);
  return data.data;
}

/** 내보내기는 파일이라 봉투가 없다 — 브라우저가 저장할 수 있게 그대로 내려온다. */
export function exportUrl(
  from: string,
  to: string,
  format: "csv" | "xlsx",
): string {
  return `/ledger/export?from=${from}&to=${to}&format=${format}`;
}

export async function downloadExport(
  from: string,
  to: string,
  format: "csv" | "xlsx",
): Promise<Blob> {
  const { data } = await client.get<Blob>(exportUrl(from, to, format), {
    responseType: "blob",
  });
  return data;
}

/* ─── 자동 분류 규칙 (`LDG-062`) ───────────────────────────────────── */

export type LedgerMatchType = "CONTAINS" | "STARTS_WITH" | "EQUALS";

export interface AutoRuleView {
  id: number;
  keyword: string;
  matchType: LedgerMatchType;
  categoryId: number;
  categoryName: string | null;
  priority: number;
  enabled: boolean;
}

export async function fetchAutoRules(): Promise<AutoRuleView[]> {
  const { data } =
    await client.get<ApiEnvelope<AutoRuleView[]>>("/ledger/auto-rules");
  return data.data;
}

export async function createAutoRule(body: {
  keyword: string;
  matchType: LedgerMatchType;
  categoryId: number;
}): Promise<AutoRuleView> {
  const { data } = await client.post<ApiEnvelope<AutoRuleView>>(
    "/ledger/auto-rules",
    body,
  );
  return data.data;
}

export async function updateAutoRule(
  id: number,
  body: { enabled?: boolean; categoryId?: number; keyword?: string },
): Promise<AutoRuleView> {
  const { data } = await client.patch<ApiEnvelope<AutoRuleView>>(
    `/ledger/auto-rules/${id}`,
    body,
  );
  return data.data;
}

export async function deleteAutoRule(id: number): Promise<void> {
  await client.delete(`/ledger/auto-rules/${id}`);
}

/* ─── 포인트·마일리지 (`LDG-006`) ──────────────────────────────────── */

/**
 * **총자산·순자산 어디에도 들어가지 않는다.** 포인트는 쓸 수 있는 곳이 정해진 외상이지
 * 돈이 아니고, 섞는 순간 「자산이 얼마인가」가 답할 수 없는 질문이 된다.
 */
export interface PointView {
  id: number;
  name: string;
  unit: string;
  balance: number;
  expiresOn: string | null;
  /** 소멸까지 남은 날. 서버가 센다 — 화면이 세면 자정 언저리에 다른 날짜를 말한다. */
  daysLeft: number | null;
  expiringSoon: boolean;
  memo: string | null;
  displayOrder: number;
}

export async function fetchPoints(): Promise<PointView[]> {
  const { data } = await client.get<ApiEnvelope<PointView[]>>("/ledger/points");
  return data.data;
}

export async function createPoint(body: {
  name: string;
  unit: string;
  balance?: number;
  expiresOn?: string | null;
  memo?: string | null;
}): Promise<PointView> {
  const { data } = await client.post<ApiEnvelope<PointView>>(
    "/ledger/points",
    body,
  );
  return data.data;
}

export async function updatePoint(
  id: number,
  body: {
    name?: string;
    unit?: string;
    balance?: number;
    expiresOn?: string | null;
    clearExpiry?: boolean;
    memo?: string | null;
  },
): Promise<PointView> {
  const { data } = await client.patch<ApiEnvelope<PointView>>(
    `/ledger/points/${id}`,
    body,
  );
  return data.data;
}

export async function deletePoint(id: number): Promise<void> {
  await client.delete(`/ledger/points/${id}`);
}
