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

/** 청구서 한 장. 산식은 서버가 계산한 값이라 목에서도 합을 맞춰 둔다. */
export function statementView(overrides: Record<string, unknown> = {}) {
  const breakdown = {
    usage: 842000,
    installment: 0,
    carriedOver: 0,
    interestFee: 0,
    adjustment: 0,
    refund: 0,
    discount: 0,
    billed: 842000,
    paid: 0,
    remaining: 842000,
    ...((overrides.breakdown as Record<string, number>) ?? {}),
  };
  return {
    id: 7,
    cardAssetId: 3,
    cycleStart: "2026-08-01",
    cycleEnd: "2026-08-31",
    paymentDate: "2026-09-14",
    status: "COLLECTING",
    overdue: false,
    paidOn: null,
    carriedToStatementId: null,
    ...overrides,
    breakdown,
  };
}

export function cardView(overrides: Record<string, unknown> = {}) {
  return {
    id: 3,
    name: "신한 Deep Dream",
    accountLast4: "1234",
    cycleStartDay: 1,
    cycleCloseDay: 99,
    paymentDay: 14,
    paymentAssetId: 1,
    paymentAssetName: "급여통장",
    creditLimit: 5000000,
    hasCycle: true,
    unpaidAmount: 842000,
    currentStatement: statementView(),
    // 조건을 안 걸어 둔 카드가 기본이다. 0%로 그리면 「못 채웠다」로 읽힌다.
    usageGoal: null,
    ...overrides,
  };
}

/** 정기 항목 한 줄. 주기 문구·월 환산·다음 결제일은 서버가 만들어 준다. */
export function recurringView(overrides: Record<string, unknown> = {}) {
  return {
    id: 12,
    name: "넷플릭스 프리미엄",
    kind: "SUBSCRIPTION",
    txType: "EXPENSE",
    amount: 17000,
    amountType: "FIXED",
    assetId: 1,
    assetName: "급여통장",
    counterAssetId: null,
    categoryId: null,
    categoryName: null,
    freqType: "MONTHLY_DAY",
    freqInterval: null,
    freqDay: 31,
    freqMonth: null,
    freqLabel: "매월 31일",
    businessDayPolicy: "AS_IS",
    startDate: "2026-01-31",
    endDate: null,
    pausedFrom: null,
    pausedTo: null,
    status: "ACTIVE",
    endedOn: null,
    cancelUrl: null,
    memo: null,
    nextDate: "2026-08-31",
    monthlyEquivalent: 17000,
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
  cards?: ReturnType<typeof cardView>[];
  installmentOutstanding?: number;
  statements?: ReturnType<typeof statementView>[];
  statementTransactions?: ReturnType<typeof transactionView>[];
  recurring?: ReturnType<typeof recurringView>[];
  recurringStats?: {
    monthlyFixedTotal?: number;
    yearlyTotal?: number;
    subscriptionCount?: number;
    activeCount?: number;
  };
  recurringSignals?: Partial<{
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
  }>;
  recurringOverdue?: {
    recurringId: number;
    name: string;
    occurrenceDate: string;
    amount: number;
    daysOverdue: number;
    note: string | null;
  }[];
  budgetCategories?: {
    categoryId: number | null;
    name: string;
    amount: number;
    spent: number;
    scheduled: number;
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
    /**
     * 관점별 합계. 넘기면 `?perspective=`가 이 표를 탄다 —
     * <b>토글이 정말 다른 숫자를 부르는지</b>는 이렇게만 확인할 수 있다.
     */
    byPerspective?: Partial<Record<"SPEND" | "BILLING", number>>;
    perspectiveDiff?: {
      other?: "SPEND" | "BILLING";
      otherTotal?: number;
      diff?: number;
      reason?: string | null;
    };
    byAsset?: {
      assetId: number;
      assetName: string | null;
      amount: number;
      share: number;
    }[];
    fixedVsVariable?: {
      fixed: number;
      variable: number;
      unclassified: number;
    };
    monthly?: {
      month: string;
      expense: number;
      income: number;
      fixed: number;
      variable: number;
      unclassified: number;
      netWorth: number | null;
    }[];
    settlement?: {
      year?: number;
      income?: number;
      expense?: number;
      savingRate?: number | null;
      highestMonth?: string | null;
      lowestMonth?: string | null;
    };
  };
  /** 가져오기. `analyze`는 열 맞추기 화면이, `preview`는 확인 화면이 읽는다. */
  importAnalyze?: {
    headers?: string[];
    sample?: string[][];
    totalRows?: number;
  };
  importPreview?: {
    rows?: {
      rowNumber: number;
      occurredOn: string | null;
      type: string | null;
      amount: number | null;
      title: string | null;
      memo?: string | null;
      categoryId?: number | null;
      categoryName?: string | null;
      error?: string | null;
      duplicateOf?: number | null;
      assetId?: number | null;
      assetName?: string | null;
    }[];
  };
  /** 실행 결과. 요청 본문은 이 환경에서 볼 수 없어 응답만 정한다. */
  importExecute?: { inserted?: number; skipped?: number };
  importBatches?: {
    id: number;
    source: string;
    fileName?: string | null;
    rowCount?: number;
    insertedCount?: number;
    createdAt?: string;
    revertedAt?: string | null;
  }[];
  autoRules?: {
    id: number;
    keyword: string;
    matchType?: string;
    categoryId: number;
    categoryName?: string | null;
    priority?: number;
    enabled?: boolean;
  }[];
  points?: {
    id: number;
    name: string;
    unit?: string;
    balance: number;
    expiresOn?: string | null;
    daysLeft?: number | null;
    expiringSoon?: boolean;
    memo?: string | null;
    displayOrder?: number;
  }[];
  /** 복합 검색 결과. `truncated`를 켜면 「잘렸다」 경고를 확인할 수 있다. */
  search?: {
    items?: ReturnType<typeof transactionView>[];
    total?: number;
    truncated?: boolean;
  };
  balanceCurve?: {
    currentBalance?: number;
    points?: { date: string; delta: number; balance: number }[];
    minBalance?: { date: string; amount: number; reason: string | null };
    firstNegativeDate?: string | null;
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
  /** 카테고리 속성 PATCH 본문. 화면이 무엇을 보냈는지로만 확인할 수 있다. */
  const categoryAttributes: Record<string, unknown>[] = [];
  const reverts: number[] = [];
  const autoRuleWrites: Record<string, unknown>[] = [];
  const payments: Record<string, unknown>[] = [];
  const budgets: Record<string, unknown>[] = [];
  const duplicated: Record<string, unknown>[] = [];
  const bulkSent: Record<string, unknown>[][] = [];
  /** 자산 생성 본문. 「무엇을 보냈는가」로만 체크카드 연결 규칙을 확인할 수 있다. */
  const assetsCreated: Record<string, unknown>[] = [];
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
    http.post(`${API_BASE}/ledger/assets`, async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      assetsCreated.push(body);
      return ok(assetView({ id: 900, ...body }));
    }),
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
          costType: null,
          excludeFromCardGoal: false,
          excludeFromSettlement: false,
          children: [],
        },
      ]),
    ),
    http.patch(`${API_BASE}/ledger/categories/:id`, async ({ request }) => {
      categoryAttributes.push(
        (await request.json()) as Record<string, unknown>,
      );
      return ok({
        id: 21,
        flow: "EXPENSE",
        name: "식비",
        parentId: null,
        color: null,
        icon: null,
        displayOrder: 0,
        archived: false,
        costType: "VARIABLE",
        excludeFromCardGoal: false,
        excludeFromSettlement: false,
        children: [],
      });
    }),
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
        // 달·오늘을 박아 두면 다음 달로 넘어가는 날 화면의 「이번 달」과 어긋난다.
        month: todayIso().slice(0, 7),
        todayLine: todayIso(),
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
    http.get(`${API_BASE}/ledger/cards`, () =>
      ok({
        cards: options.cards ?? [],
        installmentOutstanding: options.installmentOutstanding ?? 0,
      }),
    ),
    http.get(`${API_BASE}/ledger/cards/:id/statements`, () =>
      ok(options.statements ?? []),
    ),
    http.get(`${API_BASE}/ledger/statements/:id/transactions`, () =>
      ok(options.statementTransactions ?? []),
    ),
    http.post(`${API_BASE}/ledger/statements/:id/pay`, async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      payments.push(body);
      const base = options.statements?.[0] ?? statementView();
      const remaining = (base.breakdown as Record<string, number>).remaining;
      const paid = (body.amount as number | null) ?? remaining;
      return ok(
        statementView({
          ...base,
          status: paid >= remaining ? "PAID" : "PARTIAL",
          paidOn: body.paidOn ?? base.paymentDate,
          breakdown: {
            ...(base.breakdown as Record<string, number>),
            paid,
            remaining: Math.max(remaining - paid, 0),
          },
        }),
      );
    }),
    http.get(`${API_BASE}/ledger/recurring`, () =>
      ok({
        items: options.recurring ?? [],
        stats: {
          monthlyFixedTotal: options.recurringStats?.monthlyFixedTotal ?? 0,
          yearlyTotal: options.recurringStats?.yearlyTotal ?? 0,
          subscriptionCount: options.recurringStats?.subscriptionCount ?? 0,
          activeCount: options.recurringStats?.activeCount ?? 0,
        },
        signals: {
          priceIncreased: options.recurringSignals?.priceIncreased ?? [],
          trialEnding: options.recurringSignals?.trialEnding ?? [],
          longUnchanged: options.recurringSignals?.longUnchanged ?? [],
          noEndDate: options.recurringSignals?.noEndDate ?? [],
        },
        overdue: options.recurringOverdue ?? [],
      }),
    ),
    http.get(`${API_BASE}/ledger/recurring/:id/history`, () =>
      ok({ amounts: [], missed: [] }),
    ),
    http.post(`${API_BASE}/ledger/recurring/:id/end`, () =>
      ok({ reverted: 0, message: "해지했습니다." }),
    ),
    http.post(`${API_BASE}/ledger/recurring/:id/pause`, () =>
      ok(recurringView({ status: "PAUSED" })),
    ),
    http.post(`${API_BASE}/ledger/recurring/:id/resume`, () =>
      ok(recurringView()),
    ),
    http.put(`${API_BASE}/ledger/budget`, async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      budgets.push(body);
      return ok({
        period: "2026-08",
        periodStart: "2026-08-01",
        periodEnd: "2026-08-31",
        totalAmount: body.totalAmount as number,
        fixedCostTotal: 0,
        spendable: body.totalAmount as number,
        spent: options.budget?.spent ?? 0,
        scheduled: options.budget?.scheduled ?? 0,
        remaining: (body.totalAmount as number) - (options.budget?.spent ?? 0),
        daysLeft: 4,
        dailyAllowance: 0,
        categories: options.budgetCategories ?? [],
      });
    }),
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
        categories: options.budgetCategories ?? [],
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
    http.get(`${API_BASE}/ledger/stats`, ({ request }) => {
      const asked =
        (new URL(request.url).searchParams.get("perspective") as
          | "SPEND"
          | "BILLING"
          | null) ?? "SPEND";
      const base = options.stats?.total ?? 0;
      // 관점별 합계를 안 넘겼으면 둘이 같다 — 할부가 없는 달이 그렇다.
      const total = options.stats?.byPerspective?.[asked] ?? base;
      const other = asked === "SPEND" ? "BILLING" : "SPEND";
      const otherTotal = options.stats?.byPerspective?.[other] ?? base;

      return ok({
        period: { start: "2026-08-01", end: "2026-08-31", label: "2026-08" },
        perspective: asked,
        total,
        byCategory: options.stats?.byCategory ?? [],
        byAsset: options.stats?.byAsset ?? [],
        fixedVsVariable: options.stats?.fixedVsVariable ?? {
          fixed: 0,
          variable: 0,
          unclassified: total,
        },
        monthly: options.stats?.monthly ?? [
          {
            month: "2026-08",
            expense: total,
            income: 0,
            fixed: 0,
            variable: 0,
            unclassified: total,
            netWorth: null,
          },
        ],
        settlement: {
          year: 2026,
          income: 0,
          expense: total,
          savingRate: null,
          highestMonth: null,
          lowestMonth: null,
          ...options.stats?.settlement,
        },
        perspectiveDiff: {
          other,
          otherTotal,
          diff: otherTotal - total,
          reason:
            otherTotal === total
              ? null
              : (options.stats?.perspectiveDiff?.reason ?? "할부 때문"),
          ...options.stats?.perspectiveDiff,
        },
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
      });
    }),
    http.post(`${API_BASE}/ledger/stats/search`, () => {
      const items = options.search?.items ?? [];
      return ok({
        items,
        count: items.length,
        total:
          options.search?.total ??
          items.reduce((sum, item) => sum + item.amount, 0),
        truncated: options.search?.truncated ?? false,
      });
    }),
    http.get(`${API_BASE}/ledger/upcoming/balance-curve`, () => {
      const points = options.balanceCurve?.points ?? [];
      const lowest = points.reduce<number | null>(
        (min, point) =>
          min === null || point.balance < min ? point.balance : min,
        null,
      );
      return ok({
        from: "2026-08-28",
        to: "2026-09-27",
        currentBalance: options.balanceCurve?.currentBalance ?? 0,
        points,
        minBalance: options.balanceCurve?.minBalance ?? {
          date: points[0]?.date ?? "2026-08-28",
          amount: lowest ?? 0,
          reason: null,
        },
        firstNegativeDate: options.balanceCurve?.firstNegativeDate ?? null,
      });
    }),
    http.patch(`${API_BASE}/ledger/cards/:id/usage-goal`, () =>
      HttpResponse.json({ code: "OK", data: null }),
    ),
    http.post(`${API_BASE}/ledger/import/analyze`, () =>
      ok({
        headers: options.importAnalyze?.headers ?? ["날짜", "내용", "금액"],
        sample: options.importAnalyze?.sample ?? [
          ["2026-08-10", "스타벅스 역삼", "-5500"],
        ],
        totalRows: options.importAnalyze?.totalRows ?? 1,
        presets: [
          {
            id: 1,
            name: "카드사 명세서",
            mapping: { date: 0, title: 1, amount: 2 },
            skipRows: 1,
            dateFormat: null,
            builtIn: true,
          },
        ],
      }),
    ),
    http.post(`${API_BASE}/ledger/import/preview`, () => {
      const rows = (options.importPreview?.rows ?? []).map((row) => ({
        memo: null,
        categoryId: null,
        categoryName: null,
        error: null,
        duplicateOf: null,
        assetId: 1,
        assetName: "급여통장",
        ...row,
      }));
      return ok({
        rows,
        totalRows: rows.length,
        // 서버가 센다 — 화면이 다시 세면 어느 쪽이 맞는지 알 수 없다.
        duplicateCount: rows.filter((row) => row.duplicateOf !== null).length,
        errorCount: rows.filter((row) => row.error !== null).length,
      });
    }),
    /*
      실행 결과만 돌려준다. **요청 본문은 확인할 수 없다** — jsdom/undici가 multipart의
      파트 내용을 비운 채 보내서, 어떤 파서를 써도 빈 값이 나온다.
      「어느 줄을 보냈는가」는 BE 통합 테스트(LedgerImportTest)와 실제 실행이 지킨다.
    */
    http.post(`${API_BASE}/ledger/import/execute`, () =>
      ok({
        batchId: 1,
        inserted: options.importExecute?.inserted ?? 1,
        skipped: options.importExecute?.skipped ?? 0,
      }),
    ),
    http.get(`${API_BASE}/ledger/import/batches`, () =>
      ok(
        (options.importBatches ?? []).map((batch) => ({
          fileName: "sample.csv",
          rowCount: 10,
          insertedCount: 10,
          createdAt: "2026-08-28T00:00:00Z",
          revertedAt: null,
          ...batch,
        })),
      ),
    ),
    http.post(`${API_BASE}/ledger/import/batches/:id/revert`, ({ params }) => {
      reverts.push(Number(params.id));
      return ok({ batchId: Number(params.id), reverted: 3 });
    }),
    http.get(`${API_BASE}/ledger/auto-rules`, () =>
      ok(
        (options.autoRules ?? []).map((rule) => ({
          matchType: "CONTAINS",
          categoryName: "카페/간식",
          priority: 0,
          enabled: true,
          ...rule,
        })),
      ),
    ),
    http.post(`${API_BASE}/ledger/auto-rules`, async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      autoRuleWrites.push(body);
      return ok({
        id: 99,
        matchType: "CONTAINS",
        categoryName: "카페/간식",
        priority: 0,
        enabled: true,
        ...body,
      });
    }),
    http.patch(
      `${API_BASE}/ledger/auto-rules/:id`,
      async ({ request, params }) => {
        const body = (await request.json()) as Record<string, unknown>;
        autoRuleWrites.push({ id: Number(params.id), ...body });
        return ok({
          id: Number(params.id),
          keyword: "스타벅스",
          matchType: "CONTAINS",
          categoryId: 21,
          categoryName: "카페/간식",
          priority: 0,
          enabled: true,
          ...body,
        });
      },
    ),
    http.delete(`${API_BASE}/ledger/auto-rules/:id`, () =>
      HttpResponse.json({ code: "OK", data: null }),
    ),
    http.get(`${API_BASE}/ledger/points`, () =>
      ok(
        (options.points ?? []).map((point) => ({
          unit: "포인트",
          expiresOn: null,
          daysLeft: null,
          expiringSoon: false,
          memo: null,
          displayOrder: 0,
          ...point,
        })),
      ),
    ),
    http.post(`${API_BASE}/ledger/points`, async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      return ok({
        id: 99,
        unit: "포인트",
        balance: 0,
        expiresOn: null,
        daysLeft: null,
        expiringSoon: false,
        memo: null,
        displayOrder: 0,
        ...body,
      });
    }),
    http.delete(`${API_BASE}/ledger/points/:id`, () =>
      HttpResponse.json({ code: "OK", data: null }),
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

  return Object.assign(created, {
    assetsCreated,
    duplicated,
    bulkSent,
    occurrenceActions,
    categoryAttributes,
    reverts,
    autoRuleWrites,
    payments,
    budgets,
  });
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
