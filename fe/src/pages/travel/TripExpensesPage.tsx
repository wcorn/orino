import { ReceiptText } from "lucide-react";
import { useState } from "react";
import { useParams } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { LoadingText } from "@/components/ui/loading-text";
import { OfflineBanner } from "@/features/travel/board/OfflineBanner";
import { BudgetModal } from "@/features/travel/expense/BudgetModal";
import { ExpenseBudgetCard } from "@/features/travel/expense/ExpenseBudgetCard";
import { ExpenseDayCard } from "@/features/travel/expense/ExpenseDayCard";
import {
  usePutTripBudget,
  useTripExpenses,
} from "@/features/travel/hooks/useTripExpensesQuery";
import { useOnline } from "@/shared/lib/useOnline";

/**
 * 경비 `/travel/trips/:tripId/expenses` (S-11).
 *
 * <p><b>여행은 돈을 적는 곳이 아니라, 이미 적힌 돈을 여행이라는 렌즈로 다시 보는 곳이다.</b>
 * 그래서 이 화면에 편집이 없다 — 줄을 누르면 가계부의 지출 상세가 열린다(D-35).
 *
 * <p>합계도 그룹도 서버가 묶어 준 것을 그대로 그린다. 화면이 다시 세면 「출발 전 82만」과
 * 「총 123.5만」이 서로 다른 순간의 값을 말하게 된다.
 */
export function TripExpensesPage() {
  const { tripId: tripIdParam } = useParams();
  const tripId = Number(tripIdParam);

  const [budgetOpen, setBudgetOpen] = useState(false);
  /** 펼쳐 둔 묶음. 기본은 오늘 하나뿐이다 — 서른 개를 다 펼치면 아무것도 안 보인다. */
  const [opened, setOpened] = useState<string[] | null>(null);

  // 오프라인은 조회 전용이다. 큐잉하지 않는다(D-33).
  const online = useOnline();

  const { data, isPending, isError } = useTripExpenses(tripId);
  const putBudget = usePutTripBudget(tripId);

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

  const toggle = (key: string) =>
    setOpened(
      openKeys.includes(key)
        ? openKeys.filter((k) => k !== key)
        : [...openKeys, key],
    );

  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-5">
      <PageHeader
        title="경비"
        description={describe(data)}
        actions={
          <Button
            type="button"
            variant="outline"
            disabled={!online}
            onClick={() => setBudgetOpen(true)}
          >
            예산 정하기
          </Button>
        }
      />

      {!online && <OfflineBanner what="경비" />}

      <ExpenseBudgetCard data={data} onEditBudget={() => setBudgetOpen(true)} />

      {/*
        미분류는 경고가 아니라 <b>할 일</b>이다. 「카테고리만 채우면 끝나요」가 붙는 이유 —
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
            카테고리만 채우면 끝나요
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
          />
        ))}
      </div>

      {/* 이 한 줄이 「장부는 가계부 하나뿐이다」를 화면에서 설명한다(§3). */}
      <p className="text-muted-foreground text-[13px]">
        모든 지출은 가계부 원장에 쌓입니다. 이 화면은 그 위의 읽기 뷰예요 — 줄을
        누르면 가계부의 지출 상세가 열립니다.
      </p>

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

/** 「4일차 · 오사카」. 여행이 끝났으면 총 일수로 말한다. */
function describe(data: {
  status: string;
  todayDayNumber: number | null;
  totals: { days: number };
  groups: { key: string; cityName: string | null }[];
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
  return today?.cityName
    ? `${data.todayDayNumber}일차 · ${today.cityName}`
    : `${data.todayDayNumber}일차`;
}
