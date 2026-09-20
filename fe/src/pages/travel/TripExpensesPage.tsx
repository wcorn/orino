import { Plus, ReceiptText } from "lucide-react";
import { useMemo, useState } from "react";
import { Navigate, useParams } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { LoadingText } from "@/components/ui/loading-text";
import type {
  ExpenseCreateBody,
  ExpenseRow,
  ExpenseUpdateBody,
  TripExpenses,
} from "@/features/travel/api/expenses";
import { isTripNotFound } from "@/features/travel/api/travel";
import { OfflineBanner } from "@/features/travel/board/OfflineBanner";
import { BudgetModal } from "@/features/travel/expense/BudgetModal";
import { ExpenseBudgetCard } from "@/features/travel/expense/ExpenseBudgetCard";
import { ExpenseDayCard } from "@/features/travel/expense/ExpenseDayCard";
import { ExpenseQuickSheet } from "@/features/travel/expense/ExpenseQuickSheet";
import { useBoard } from "@/features/travel/hooks/useBoard";
import { useTrip } from "@/features/travel/hooks/useTrip";
import {
  useCreateTripExpense,
  useDeleteTripExpense,
  usePutTripBudget,
  useTripExpenses,
  useUpdateTripExpense,
} from "@/features/travel/hooks/useTripExpensesQuery";
import { cityOn } from "@/features/travel/lib/baseCity";
import {
  formatDateWithWeekday,
  todayLocal,
} from "@/features/travel/lib/tripStatus";
import { TripBreadcrumb } from "@/features/travel/trip/TripBreadcrumb";
import { toast, toastUndo } from "@/shared/lib/toast";
import { useOnline } from "@/shared/lib/useOnline";

/**
 * 경비 `/travel/trips/:tripId/expenses` (S-11).
 *
 * <p><b>여행 전용 장부다.</b> 예전에는 가계부 원장 위의 읽기 뷰였고 줄을 누르면 가계부
 * 지출 상세가 열렸다(D-35). 원장이 없어지면서 적고·고치고·지우는 일이 전부 여기로 왔다.
 *
 * <p>그래도 <b>합계도 그룹도 서버가 묶어 준 것을 그대로 그린다.</b> 화면이 다시 세면
 * 「출발 전 82만」과 「총 123.5만」이 서로 다른 순간의 값을 말하게 된다.
 */
export function TripExpensesPage() {
  const { tripId: tripIdParam } = useParams();
  const tripId = Number(tripIdParam);

  const [budgetOpen, setBudgetOpen] = useState(false);
  const [sheetOpen, setSheetOpen] = useState(false);
  /** 고치는 중인 줄. `null`이면 새로 적는 것이다 — 시트는 한 벌이다(§7). */
  const [editing, setEditing] = useState<ExpenseRow | null>(null);
  /** 펼쳐 둔 묶음. 기본은 오늘 하나뿐이다 — 서른 개를 다 펼치면 아무것도 안 보인다. */
  const [opened, setOpened] = useState<string[] | null>(null);

  // 오프라인은 조회 전용이다. 큐잉하지 않는다(D-33).
  const online = useOnline();

  const { data, isPending, isError, error } = useTripExpenses(tripId);
  // 브레드크럼이 쓰는 이름 하나. 준비 화면과 같은 캐시를 탄다.
  const { data: trip } = useTrip(tripId);
  const putBudget = usePutTripBudget(tripId);
  const createExpense = useCreateTripExpense(tripId);
  const updateExpense = useUpdateTripExpense(tripId);
  const deleteExpense = useDeleteTripExpense(tripId);
  /**
   * 통화 기본값을 정하려면 <b>오늘 있는 도시</b>가 필요한데, 경비 응답에는 도시 이름만 있고
   * 통화가 없다. 보드가 그 값을 이미 들고 있으므로 <b>시트를 열 때만</b> 부른다 —
   * 경비 화면을 열 때마다 보드까지 부르면 그건 이 화면의 비용이 아니다.
   */
  const { data: board } = useBoard(tripId, {}, { enabled: sheetOpen });

  /**
   * 결제수단 자동완성 후보 — <b>이 여행에서 이미 쓴 값</b>뿐이다(§4.2).
   *
   * <p>전역 목록을 서버에서 받아오지 않는다. 받아오는 순간 그게 자산 테이블이고,
   * 그때부터 이름 고치기·합치기·숨기기가 따라온다.
   */
  const paymentMethods = useMemo(() => usedPaymentMethods(data), [data]);

  // 없는 여행이면 고르게 한다 — 준비와 같은 이유다(뒤로 가기에 죽은 URL을 남기지 않는다).
  if (isError && isTripNotFound(error)) {
    return <Navigate to="/travel/expenses" replace />;
  }

  if (isError) {
    return (
      <div className="mx-auto max-w-[720px]">
        <Alert variant="destructive">경비를 불러오지 못했어요.</Alert>
      </div>
    );
  }

  if (isPending || !data) {
    return (
      <div className="grid min-h-[40svh] place-items-center">
        <LoadingText />
      </div>
    );
  }

  const todayKey =
    data.todayDayNumber === null ? null : `DAY-${data.todayDayNumber}`;
  // 첫 렌더에서는 오늘만 펼친다. 사용자가 한 번이라도 접거나 펼치면 그 선택을 따른다.
  const openKeys = opened ?? (todayKey === null ? [] : [todayKey]);

  /** 오늘 있는 도시. 보드를 아직 안 받았으면 없다 — 시트가 원화로 시작한다. */
  const todayCity =
    board && board.selectedDate ? cityOn(board.days, board.selectedDate) : null;
  /**
   * 적히는 날짜. 여행 중이면 <b>그 날짜의 도시 시계로 판정한 오늘</b>이고(서버가 준 값),
   * 아니면 기기의 오늘이다 — 다녀온 뒤에 산 것은 「다녀온 뒤」로 묶여야 한다.
   */
  const occurredOn =
    data.groups.find((group) => group.key === todayKey)?.date ?? todayLocal();

  const toggle = (key: string) =>
    setOpened(
      openKeys.includes(key)
        ? openKeys.filter((k) => k !== key)
        : [...openKeys, key],
    );

  const openNew = () => {
    setEditing(null);
    setSheetOpen(true);
  };

  const openEdit = (row: ExpenseRow) => {
    setEditing(row);
    setSheetOpen(true);
  };

  const create = (body: ExpenseCreateBody) =>
    createExpense.mutate(body, { onSuccess: () => setSheetOpen(false) });

  const update = (expenseId: number, body: ExpenseUpdateBody) =>
    updateExpense.mutate(
      { expenseId, body },
      { onSuccess: () => setSheetOpen(false) },
    );

  /** 지난 예정을 확정한다. 올려 주는 배치가 없으므로 이 길이 유일하다(§4.3). */
  const confirm = (row: ExpenseRow) =>
    updateExpense.mutate({
      expenseId: row.expenseId,
      body: { status: "CONFIRMED" },
    });

  /**
   * 지운다. <b>상쇄하지 않는다</b>(§4.4) — 여행 경비는 원장이 아니라 지출 목록이다.
   *
   * <p>되돌리기는 <b>같은 값으로 다시 적는 것</b>이다. 지운 행을 살리는 API를 두지
   * 않는다 — 그건 휴지통의 시작이고, 여행 중에 열 화면이 아니다. 그래서 되돌린 줄은
   * 새 id를 받는다. 대신 합계와 그룹은 언제나 서버가 센 값 그대로다.
   */
  const remove = (row: ExpenseRow) => {
    setSheetOpen(false);
    deleteExpense.mutate(row.expenseId, {
      onSuccess: () =>
        toastUndo("지웠어요", {
          onUndo: () =>
            createExpense.mutate(restoreBodyOf(row), {
              onSuccess: () => toast("되돌렸어요", "success"),
            }),
        }),
    });
  };

  const pending =
    createExpense.isPending ||
    updateExpense.isPending ||
    deleteExpense.isPending;

  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-5">
      <TripBreadcrumb tripId={tripId} tripTitle={trip?.title} current="경비" />
      <PageHeader
        title="경비"
        description={describe(data)}
        actions={
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              disabled={!online}
              onClick={() => setBudgetOpen(true)}
            >
              예산 정하기
            </Button>
            {/* 오프라인이면 진입 자체를 막는다 — 큐에 쌓아 나중에 보내지 않는다(D-33). */}
            <Button
              type="button"
              className="hidden sm:inline-flex"
              disabled={!online}
              onClick={openNew}
            >
              <Plus className="size-4" />
              지출 적기
            </Button>
          </div>
        }
      />

      {!online && <OfflineBanner what="경비" />}

      <ExpenseBudgetCard
        data={data}
        offline={!online}
        onEditBudget={() => setBudgetOpen(true)}
      />

      {/*
        미분류는 경고가 아니라 <b>할 일</b>이다. 「분류만 채우면 끝나요」가 붙는 이유 —
        무엇을 하면 이 줄이 사라지는지 함께 말하지 않으면 그냥 거슬리기만 한다.
      */}
      {data.unsortedCount > 0 && (
        <div
          className="flex items-center gap-2 rounded-lg px-3.5 py-3 text-sm"
          style={{
            background: "color-mix(in oklab, var(--warning) 18%, var(--card))",
          }}
        >
          <ReceiptText className="size-4 shrink-0" />
          정리할 내역 {data.unsortedCount}건
          <span className="text-muted-foreground ml-auto text-[13px]">
            분류만 채우면 끝나요
          </span>
        </div>
      )}

      <div className="flex flex-col gap-3.5">
        {data.groups.map((group) => (
          <ExpenseDayCard
            key={group.key}
            group={group}
            open={openKeys.includes(group.key)}
            onToggleOpen={() => toggle(group.key)}
            today={group.key === todayKey}
            onEdit={openEdit}
            onConfirm={confirm}
            offline={!online}
          />
        ))}
      </div>

      {/* 이 한 줄이 「여행 경비는 여행 안에서만 산다」를 화면에서 설명한다(§2). */}
      <p className="text-muted-foreground text-[13px]">
        여기 적은 지출은 이 여행 안에만 남습니다 — 줄을 누르면 바로 고칠 수
        있어요.
      </p>

      {/*
        모바일 FAB. 데스크톱은 헤더의 「지출 적기」가 같은 역할을 하므로 좁은 화면에만 둔다 —
        현지에서 한 손으로 두드리는 화면이라 엄지가 닿는 곳에 있어야 한다.
      */}
      {online && (
        <button
          type="button"
          aria-label="지출 적기"
          onClick={openNew}
          className="bg-primary text-primary-foreground fixed right-5 bottom-5 grid size-14 place-items-center rounded-full sm:hidden"
          style={{
            boxShadow:
              "0 6px 18px color-mix(in oklab, var(--primary) 40%, transparent)",
          }}
        >
          <Plus className="size-6" />
        </button>
      )}

      <ExpenseQuickSheet
        open={sheetOpen}
        onOpenChange={setSheetOpen}
        tripId={tripId}
        cityName={todayCity?.name ?? null}
        cityCurrency={todayCity?.currency ?? null}
        occurredOn={occurredOn}
        editing={editing}
        paymentMethods={paymentMethods}
        onCreate={create}
        onUpdate={update}
        onDelete={remove}
        pending={pending}
      />

      <BudgetModal
        open={budgetOpen}
        onOpenChange={setBudgetOpen}
        current={data.budget?.amount ?? null}
        pending={putBudget.isPending}
        onSave={(amount) =>
          putBudget.mutate(amount, { onSuccess: () => setBudgetOpen(false) })
        }
      />
    </div>
  );
}

/** 그 여행에서 이미 쓴 결제수단. 최근에 쓴 것이 앞에 오도록 뒤에서부터 읽는다. */
function usedPaymentMethods(data: TripExpenses | undefined): string[] {
  if (!data) return [];
  const seen = new Set<string>();
  for (let i = data.groups.length - 1; i >= 0; i--) {
    const rows = data.groups[i].rows;
    for (let j = rows.length - 1; j >= 0; j--) {
      const name = rows[j].paymentMethod;
      if (name) seen.add(name);
    }
  }
  return [...seen];
}

/**
 * 되돌리기가 다시 적는 값. <b>환율은 굳은 값을 그대로 보낸다</b> — 비워 보내면 오늘
 * 고시로 다시 채워져, 지웠다 되돌린 것만으로 지난 지출의 원화 금액이 바뀐다(§4.3).
 */
function restoreBodyOf(row: ExpenseRow): ExpenseCreateBody {
  return {
    occurredOn: row.occurredOn,
    title: row.title,
    category: row.category,
    paymentMethod: row.paymentMethod,
    status: row.status,
    ...(row.fx
      ? {
          fx: {
            currency: row.fx.currency,
            amount: row.fx.amount,
            rate: row.fx.rate,
          },
        }
      : { amount: row.amount }),
  };
}

/** 「10.26 (월) · 오사카」. 여행이 끝났으면 총 일수로 말한다. */
function describe(data: {
  status: string;
  todayDayNumber: number | null;
  totals: { days: number };
  groups: { key: string; date: string | null; cityName: string | null }[];
}): string {
  if (data.status === "COMPLETED") {
    return `다녀온 여행 · 총 ${data.totals.days}일`;
  }
  if (data.todayDayNumber === null) {
    return "아직 출발 전이에요";
  }
  const today = data.groups.find(
    (group) => group.key === `DAY-${data.todayDayNumber}`,
  );
  // 오늘에 해당하는 묶음이 없으면 말할 날짜도 없다 — 지어내지 않는다.
  if (!today?.date) {
    return "";
  }
  return today.cityName
    ? `${formatDateWithWeekday(today.date)} · ${today.cityName}`
    : formatDateWithWeekday(today.date);
}
