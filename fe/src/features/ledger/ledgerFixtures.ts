import { http, HttpResponse } from "msw";

import { todayIso } from "@/features/ledger/lib/period";
import { server } from "@/test/mocks/server";

const API_BASE = "https://api.orino.dev/api";

const ok = (data: unknown) => HttpResponse.json({ code: "OK", data });

export function assetView(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    groupId: null,
    name: "급여통장",
    type: "CHECKING",
    accountLast4: null,
    displayOrder: 0,
    hidden: false,
    closedReason: null,
    maturityDate: null,
    targetAmount: null,
    linkedAssetId: null,
    linkedAssetName: null,
    balance: 1000000,
    unpaidAmount: null,
    ...overrides,
  };
}

export function transactionView(overrides: Record<string, unknown> = {}) {
  return {
    id: 10,
    type: "EXPENSE",
    status: "CONFIRMED",
    occurredOn: "2026-08-28",
    occurredAt: null,
    amount: 4500,
    assetId: 1,
    assetName: "급여통장",
    counterAssetId: null,
    counterAssetName: null,
    categoryId: 21,
    categoryName: "식비",
    title: "스타벅스 역삼",
    memo: null,
    source: "MANUAL",
    estimated: false,
    refundOfId: null,
    tags: [],
    fx: null,
    ...overrides,
  };
}

/** 예정 한 줄. 파생이라 거래 id가 없는 것이 기본이다. */
export function upcomingItem(overrides: Record<string, unknown> = {}) {
  return {
    kind: "RECURRING",
    date: "2026-08-31",
    dday: 3,
    title: "넷플릭스 프리미엄",
    amount: 17000,
    flow: "EXPENSE",
    isTransfer: false,
    overdue: false,
    estimated: false,
    categoryId: null,
    assetId: 1,
    assetName: "급여통장",
    transactionId: null,
    recurringId: 12,
    occurrenceDate: "2026-08-31",
    statementId: null,
    installmentId: null,
    ...overrides,
  };
}

export interface LedgerMockOptions {
  dashboard?: {
    spent?: number;
    scheduled?: number;
    income?: number;
    uncategorized?: number;
    overdue?: number;
    monthStartDay?: number;
    balance?: number;
    remainingOutflow?: number;
    totalAssets?: number;
    liabilities?: number;
  };
  /** 예정 4출처. 대시보드·내역 타임라인·예정 화면·캘린더가 모두 이 목록을 읽는다. */
  upcoming?: ReturnType<typeof upcomingItem>[];
  /** 예산. 대시보드의 2단 게이지가 `totalAmount`를 쓴다. */
  budget?: { totalAmount?: number; spent?: number; scheduled?: number };
  calendarDays?: {
    date: string;
    income?: number;
    expense?: number;
    scheduledIncome?: number;
    scheduledExpense?: number;
    scheduledTransfer?: number;
  }[];
  stats?: {
    total: number;
    byCategory: {
      categoryId: number | null;
      categoryName: string | null;
      amount: number;
      count: number;
      share: number;
    }[];
    previousTotal?: number;
  };
  /** 잔액 맞추기 응답의 차액. 0이면 조정 거래를 만들지 않았다는 뜻이다. */
  reconcileDifference?: number;
  templates?: {
    id: number;
    name: string;
    txType: string;
    amount: number;
    assetId: number;
    assetName: string | null;
    categoryId: number | null;
    categoryName: string | null;
    title: string | null;
    useCount: number;
  }[];
  receipts?: {
    id: number;
    objectKey: string;
    url: string;
    contentType: string | null;
    byteSize: number | null;
    displayOrder: number;
  }[];
  /** 다건 입력에서 서버가 거부하는 상황. 전부-아니면-전무를 확인할 때 쓴다. */
  bulkFails?: boolean;
  assets?: ReturnType<typeof assetView>[];
  groups?: unknown[];
  transactions?: ReturnType<typeof transactionView>[];
  monthStartDay?: number;
  /** ECB 실패를 흉내 낸다 — `rate: null`이 오지만 저장은 막히지 않아야 한다. */
  fxRate?: number | null;
  suggestions?: unknown[];
}

/**
 * 가계부 API 목. <b>MSW로 네트워크만 제어한다</b> — 훅이나 컨텍스트를 `vi.mock`으로
 * 갈아끼우지 않는다(FE 테스트 원칙).
 *
 * @returns 화면이 실제로 보낸 요청 본문. 「무엇을 보내려 했는가」가 이 모듈의 관심사다
 */
export function mockLedgerApi(options: LedgerMockOptions = {}) {
  const created: Record<string, unknown>[] = [];
  const occurrenceActions: Record<string, unknown>[] = [];
  const duplicated: Record<string, unknown>[] = [];
  const bulkSent: Record<string, unknown>[][] = [];
  const assets = options.assets ?? [assetView()];
  const transactions = options.transactions ?? [];

  server.use(
    http.get(`${API_BASE}/ledger/assets`, () =>
      ok({
        groups:
          options.groups ??
          (assets.length > 0
            ? [
                {
                  id: null,
                  name: "그 외",
                  kind: "ETC",
                  displayOrder: 0,
                  collapsed: false,
                  subtotal: assets.reduce(
                    (sum, asset) => sum + (asset.balance ?? 0),
                    0,
                  ),
                  assets,
                },
              ]
            : []),
        hidden: [],
        totalAssets: assets.reduce(
          (sum, asset) => sum + (asset.balance ?? 0),
          0,
        ),
        liabilities: 0,
        netWorth: assets.reduce((sum, asset) => sum + (asset.balance ?? 0), 0),
      }),
    ),
    // 자산 상세. 목록과 같은 자산을 쓰되 추이·분포는 이 테스트들의 관심사가 아니라 비워 둔다.
    http.get(`${API_BASE}/ledger/assets/:id`, ({ params }) => {
      const asset =
        assets.find((item) => String(item.id) === String(params.id)) ??
        assets[0];
      return ok({ asset, range: "MONTH", trend: [], categoryShare: [] });
    }),
    http.get(`${API_BASE}/ledger/assets/:id/transactions`, () =>
      ok({
        items: transactions.map((transaction) => ({
          transaction,
          runningBalance: null,
        })),
      }),
    ),
    http.get(`${API_BASE}/ledger/categories`, () =>
      ok([
        {
          id: 21,
          flow: "EXPENSE",
          name: "식비",
          parentId: null,
          color: null,
          icon: null,
          displayOrder: 0,
          archived: false,
          children: [],
        },
      ]),
    ),
    http.get(`${API_BASE}/ledger/settings`, () =>
      ok({
        monthStartDay: options.monthStartDay ?? 1,
        monthStartWeekendPolicy: "AS_IS",
        defaultAssetId: null,
        defaultPerspective: "SPEND",
      }),
    ),
    http.get(`${API_BASE}/ledger/transactions/suggest`, () =>
      ok(options.suggestions ?? []),
    ),
    http.get(`${API_BASE}/ledger/transactions`, () =>
      ok({
        todayLine: "2026-08-28",
        monthTotals: {
          income: 0,
          expense: transactions
            .filter((tx) => tx.type === "EXPENSE" && tx.status === "CONFIRMED")
            .reduce((sum, tx) => sum + (tx.amount as number), 0),
          transfer: 0,
          scheduledExpense: 0,
          scheduledIncome: 0,
          scheduledCount: transactions.filter((tx) => tx.status === "SCHEDULED")
            .length,
        },
        groups: groupByDate(transactions),
      }),
    ),
    http.get(`${API_BASE}/ledger/fx/rate`, ({ request }) => {
      const currency = new URL(request.url).searchParams.get("currency") ?? "";
      return ok({
        currency,
        rate: options.fxRate === undefined ? 8.7604 : options.fxRate,
        referenceDate: options.fxRate === null ? null : "2026-08-27",
        source: "ECB",
      });
    }),
    http.get(`${API_BASE}/ledger/dashboard`, () => {
      const spent = options.dashboard?.spent ?? 0;
      const scheduled = options.dashboard?.scheduled ?? 0;
      const balance = options.dashboard?.balance ?? 0;
      const remainingOutflow = options.dashboard?.remainingOutflow ?? 0;
      return ok({
        spending: { spent, scheduled, estimate: spent + scheduled },
        cashflow: {
          balance,
          remainingOutflow,
          remainingInflow: 0,
          monthEndBalance: balance - remainingOutflow,
          minBalance: {
            amount: balance - remainingOutflow,
            date: "2026-09-14",
            reason: remainingOutflow > 0 ? "카드 대금" : null,
          },
        },
        income: { amount: options.dashboard?.income ?? 0 },
        netWorth: {
          totalAssets: options.dashboard?.totalAssets ?? 0,
          liabilities: options.dashboard?.liabilities ?? 0,
          netWorth:
            (options.dashboard?.totalAssets ?? 0) -
            (options.dashboard?.liabilities ?? 0),
        },
        upcoming: options.upcoming ?? [],
        todo: {
          uncategorized: options.dashboard?.uncategorized ?? 0,
          overdue: options.dashboard?.overdue ?? 0,
        },
        period: {
          start: "2026-08-01",
          end: "2026-08-31",
          monthStartDay: options.dashboard?.monthStartDay ?? 1,
        },
      });
    }),
    http.get(`${API_BASE}/ledger/upcoming`, () => {
      const items = options.upcoming ?? [];
      const byKind: Record<string, number> = {};
      for (const item of items) {
        byKind[item.kind as string] = (byKind[item.kind as string] ?? 0) + 1;
      }
      const outflow = items.reduce(
        (sum, item) =>
          sum + (item.flow === "INCOME" ? 0 : (item.amount as number)),
        0,
      );
      const balance = options.dashboard?.balance ?? 0;
      return ok({
        from: "2026-08-28",
        to: "2026-09-27",
        days: 30,
        stats: {
          outflow,
          income: 0,
          currentBalance: balance,
          expectedBalance: balance - outflow,
          minBalance: {
            amount: balance - outflow,
            date: "2026-09-14",
            reason: items.length > 0 ? (items[0].title as string) : null,
          },
          count: items.length,
          byKind,
        },
        items,
      });
    }),
    http.get(`${API_BASE}/ledger/transactions/calendar`, () =>
      ok({
        month: "2026-08",
        todayLine: "2026-08-28",
        days: (options.calendarDays ?? []).map((day) => ({
          income: 0,
          expense: 0,
          scheduledIncome: 0,
          scheduledExpense: 0,
          scheduledTransfer: 0,
          ...day,
        })),
      }),
    ),
    http.get(`${API_BASE}/ledger/budget`, () =>
      ok({
        period: "2026-08",
        periodStart: "2026-08-01",
        periodEnd: "2026-08-31",
        totalAmount: options.budget?.totalAmount ?? 0,
        fixedCostTotal: 0,
        spendable: options.budget?.totalAmount ?? 0,
        spent: options.budget?.spent ?? 0,
        scheduled: options.budget?.scheduled ?? 0,
        remaining:
          (options.budget?.totalAmount ?? 0) - (options.budget?.spent ?? 0),
        daysLeft: 4,
        dailyAllowance: 0,
        categories: [],
      }),
    ),
    http.patch(
      `${API_BASE}/ledger/upcoming/occurrence`,
      async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>;
        occurrenceActions.push(body);
        return ok({
          recurringId: body.recurringId,
          name: "넷플릭스 프리미엄",
          occurrenceDate: body.occurrenceDate,
          date: body.movedTo ?? body.occurrenceDate,
          amount: body.amount ?? 17000,
          action: body.action,
          overdue: body.action === "UNPAID",
          transactionId: null,
        });
      },
    ),
    http.post(
      `${API_BASE}/ledger/upcoming/occurrence/confirm`,
      async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>;
        occurrenceActions.push(body);
        return ok({
          recurringId: body.recurringId,
          name: "실손보험",
          occurrenceDate: body.occurrenceDate,
          date: body.actualDate,
          amount: 42300,
          action: "MOVE",
          overdue: false,
          transactionId: 91,
        });
      },
    ),
    // 사이드바가 워크스페이스 안에서 미납 배지를 그리려고 부른다.
    http.get(`${API_BASE}/ledger/summary`, () =>
      ok({
        monthEstimate: 0,
        monthSpent: options.dashboard?.spent ?? 0,
        monthScheduled: 0,
        uncategorizedCount: options.dashboard?.uncategorized ?? 0,
        monthEndBalance: 0,
        remainingOutflow: options.dashboard?.remainingOutflow ?? 0,
        overdueCount: options.dashboard?.overdue ?? 0,
        period: { start: "2026-08-01", end: "2026-08-31" },
      }),
    ),
    http.get(`${API_BASE}/ledger/stats`, () =>
      ok({
        period: { start: "2026-08-01", end: "2026-08-31", label: "2026-08" },
        total: options.stats?.total ?? 0,
        byCategory: options.stats?.byCategory ?? [],
        comparison: {
          previousPeriod: {
            start: "2026-07-01",
            end: "2026-07-31",
            total: options.stats?.previousTotal ?? 0,
            diff:
              (options.stats?.total ?? 0) - (options.stats?.previousTotal ?? 0),
          },
          previousYear: {
            start: "2025-08-01",
            end: "2025-08-31",
            total: 0,
            diff: options.stats?.total ?? 0,
          },
        },
      }),
    ),
    http.post(`${API_BASE}/ledger/assets/:id/reconcile`, () =>
      ok({
        adjustmentTransactionId:
          (options.reconcileDifference ?? 0) === 0 ? null : 99,
        difference: options.reconcileDifference ?? 0,
        balanceAfter: 950000,
      }),
    ),
    http.get(`${API_BASE}/ledger/templates`, () => ok(options.templates ?? [])),
    http.post(`${API_BASE}/ledger/templates`, () =>
      ok({
        id: 99,
        name: "새 템플릿",
        txType: "EXPENSE",
        amount: 4500,
        assetId: 1,
        assetName: "급여통장",
        categoryId: null,
        categoryName: null,
        title: null,
        useCount: 0,
      }),
    ),
    http.post(`${API_BASE}/ledger/templates/:id/apply`, () =>
      ok({
        transaction: transactionView({ title: "출근 커피" }),
        savedAs: "CONFIRMED",
      }),
    ),
    http.post(
      `${API_BASE}/ledger/transactions/:id/duplicate`,
      async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>;
        duplicated.push(body);
        return ok({
          transaction: transactionView({ id: 77 }),
          savedAs: "CONFIRMED",
        });
      },
    ),
    http.post(
      `${API_BASE}/ledger/transactions/bulk-create`,
      async ({ request }) => {
        const body = (await request.json()) as {
          transactions: Record<string, unknown>[];
        };
        if (options.bulkFails) {
          // 한 줄이라도 잘못되면 서버가 통째로 거부한다 — 부분 성공 응답이 없다.
          return HttpResponse.json(
            { code: "LDG-ERR-001", message: "존재하지 않는 자산입니다." },
            { status: 404 },
          );
        }
        bulkSent.push(body.transactions);
        return ok({
          created: body.transactions.map(() => transactionView()),
          scheduledCount: 0,
        });
      },
    ),
    http.get(`${API_BASE}/ledger/transactions/:id/receipts`, () =>
      ok(options.receipts ?? []),
    ),
    http.post(`${API_BASE}/ledger/transactions`, async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      created.push(body);
      // 서버와 같은 규칙으로 판정한다. 「오늘」을 날짜로 박아 두면 그날이 지나는 순간
      // 모든 저장이 예정으로 취급된다 — 실제로 그렇게 깨졌다.
      const future = String(body.occurredOn) > todayIso();
      return ok({
        transaction: transactionView({
          ...body,
          status: future ? "SCHEDULED" : "CONFIRMED",
          amount: body.amount ?? 11213,
        }),
        savedAs: future ? "SCHEDULED" : "CONFIRMED",
      });
    }),
  );

  return Object.assign(created, { duplicated, bulkSent, occurrenceActions });
}

function groupByDate(transactions: ReturnType<typeof transactionView>[]) {
  const byDate = new Map<string, ReturnType<typeof transactionView>[]>();
  for (const transaction of transactions) {
    const date = transaction.occurredOn as string;
    byDate.set(date, [...(byDate.get(date) ?? []), transaction]);
  }
  return [...byDate.entries()].map(([date, items]) => ({
    date,
    income: items
      .filter((item) => item.type === "INCOME")
      .reduce((sum, item) => sum + (item.amount as number), 0),
    expense: items
      .filter((item) => item.type === "EXPENSE" && item.status === "CONFIRMED")
      .reduce((sum, item) => sum + (item.amount as number), 0),
    items,
  }));
}
