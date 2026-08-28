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

export interface LedgerMockOptions {
  /** 대시보드가 내리는 세 값. v1.5 블록은 **필드 자체가 없다**(D-7). */
  dashboard?: {
    spent?: number;
    income?: number;
    uncategorized?: number;
    monthStartDay?: number;
  };
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
    http.get(`${API_BASE}/ledger/dashboard`, () =>
      ok({
        spending: { spent: options.dashboard?.spent ?? 0 },
        income: { amount: options.dashboard?.income ?? 0 },
        todo: { uncategorized: options.dashboard?.uncategorized ?? 0 },
        period: {
          start: "2026-08-01",
          end: "2026-08-31",
          monthStartDay: options.dashboard?.monthStartDay ?? 1,
        },
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

  return created;
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
