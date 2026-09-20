import { useQuery } from "@tanstack/react-query";
import { useEffect, useId, useState } from "react";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { fetchExchangeRate } from "@/features/travel/api/tools";
import { formatDateWithWeekday } from "@/features/travel/lib/tripStatus";
import { cn } from "@/lib/utils";

import {
  EXPENSE_CATEGORIES,
  EXPENSE_CATEGORY_LABELS,
  type ExpenseCategory,
  type ExpenseCreateBody,
  type ExpenseRow,
  type ExpenseUpdateBody,
} from "../api/expenses";
import { formatAmount } from "../lib/money";
import { travelKeys } from "../queryKeys";
import {
  getLastPaymentMethod,
  rememberLastPaymentMethod,
} from "./lastPaymentMethod";

const KRW = "KRW";

interface ExpenseQuickSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  tripId: number;
  /** 오늘 있는 도시. 통화 기본값이 여기서 온다 — `trip.currency`는 v2.1에서 사라졌다. */
  cityName: string | null;
  cityCurrency: string | null;
  /** 새로 적을 때의 날짜(여행 기준). 서버가 준 값을 그대로 쓴다. */
  occurredOn: string;
  /** 고칠 줄. `null`이면 새로 적는 것이다 — <b>시트는 한 벌</b>이다(§7). */
  editing: ExpenseRow | null;
  /** 결제수단 자동완성 후보. <b>이 여행에서 이미 쓴 값</b>만이다(§4.2). */
  paymentMethods: string[];
  onCreate: (body: ExpenseCreateBody) => void;
  onUpdate: (expenseId: number, body: ExpenseUpdateBody) => void;
  onDelete: (row: ExpenseRow) => void;
  pending: boolean;
}

/**
 * 지출을 적고 고치는 시트(경비 독립 §6.1 · §7).
 *
 * <p><b>입력과 편집이 한 컴포넌트다.</b> 행을 누르면 여기가 그 값으로 열린다 — 필드가
 * 하나 늘 때 고칠 자리가 하나이고, 「적을 때는 되는데 고칠 때는 안 되는 것」이 생기지 않는다.
 * 예전에는 행을 누르면 가계부 지출 상세로 나갔다(D-35). 갈 곳이 없어졌다.
 *
 * <p><b>30초 안에 끝나야 한다.</b> 그래서 금액만 적고 저장할 수 있다 — 분류를 고르느라
 * 기록을 포기하느니 나중에 채운다. 안 채운 것은 「정리할 내역 N건」으로 남는다.
 *
 * <p>분류 칩은 <b>여섯 개 고정</b>이고 서버에서 목록을 받아오지 않는다. 결제수단은 입력이고,
 * 자동완성 후보는 그 여행에서 이미 쓴 값뿐이다 — 전역 목록을 만들면 그게 자산 테이블이다.
 */
export function ExpenseQuickSheet({
  open,
  onOpenChange,
  tripId,
  cityName,
  cityCurrency,
  occurredOn,
  editing,
  paymentMethods,
  onCreate,
  onUpdate,
  onDelete,
  pending,
}: ExpenseQuickSheetProps) {
  const listId = useId();
  const [amount, setAmount] = useState("");
  const [title, setTitle] = useState("");
  const [category, setCategory] = useState<ExpenseCategory | null>(null);
  const [paymentMethod, setPaymentMethod] = useState("");
  /** 사용자가 직접 고른 통화. 안 골랐으면 `null`이고 아래에서 기본값이 답한다. */
  const [pickedCurrency, setPickedCurrency] = useState<string | null>(null);

  /**
   * 열 때만 값을 다시 세운다. 고칠 줄이 있으면 그 값으로, 없으면 빈 칸으로.
   *
   * <p>남겨 두면 방금 저장한 금액이 다음 입력에 얹혀 보이고, 편집을 닫았다 새로 적을 때
   * 남의 제목이 따라 들어온다.
   *
   * <p><b>`cityCurrency`는 여기 들어오지 않는다.</b> 그 값은 시트를 연 뒤에 도착하는
   * 보드 응답에서 오는데, 의존성에 넣으면 도착하는 순간 이 효과가 한 번 더 돌아
   * <b>이미 찍어 둔 금액이 지워진다</b>. 현지에서 망이 느릴수록 잘 맞는 타이밍이라
   * 정확히 이 화면에서 가장 아픈 자리다. 통화 기본값은 상태가 아니라 파생으로 받는다.
   */
  useEffect(() => {
    if (!open) return;
    setPickedCurrency(null);
    if (editing) {
      // 외화 건이면 적힌 값은 외화다 — 원화 환산액을 보여주면 고치는 순간 두 배가 된다.
      setAmount(String(editing.fx ? editing.fx.amount : editing.amount));
      setTitle(editing.title ?? "");
      setCategory(editing.category);
      setPaymentMethod(editing.paymentMethod ?? "");
      return;
    }
    setAmount("");
    setTitle("");
    setCategory(null);
    setPaymentMethod(getLastPaymentMethod(tripId) ?? "");
  }, [open, editing, tripId]);

  /**
   * 쓸 통화. <b>고른 것 > 고치는 줄의 것 > 오늘 도시의 것 > 원화</b> 순이다.
   *
   * <p>오사카면 엔, 인천공항이면 원. 도시를 아직 모르면(보드가 오기 전이거나 기간 밖)
   * 원화로 두고, 도시가 도착하면 <b>다른 입력을 건드리지 않고</b> 칩만 따라 움직인다.
   */
  const currency =
    pickedCurrency ??
    (editing ? (editing.fx?.currency ?? KRW) : (cityCurrency ?? KRW));
  const foreign = currency !== KRW;
  /**
   * 환산 미리보기. 여행 도구의 환율을 그대로 쓴다 — 저장할 때 굳는 값은 서버가 다시
   * 정하므로 이 값은 <b>보여주기 전용</b>이다.
   *
   * <p>외화일 때만 부른다. 원화 입력에 환율 요청이 붙으면 그건 그냥 낭비다.
   * 못 받아 와도 저장은 그대로 된다 — 환율 때문에 기록을 막지 않는다(§6).
   */
  const { data: fx } = useQuery({
    queryKey: travelKeys.fx(currency, KRW),
    queryFn: () => fetchExchangeRate(currency, KRW),
    enabled: open && foreign,
    staleTime: 60 * 60 * 1000,
    retry: false,
  });

  const parsed = amount.trim() === "" ? null : Number(amount);
  const valid = parsed !== null && parsed > 0;
  const krw = !valid ? null : foreign ? convertedKrw(parsed, fx?.rate) : parsed;

  const save = () => {
    if (!valid) return;
    const label = paymentMethod.trim();
    const money = foreign
      ? // 환율은 비워 보낸다. 서버가 오늘 고시로 채우고 그 지출에 고정한다(§4.3).
        { fx: { currency, amount: parsed, rate: null } }
      : { amount: parsed };

    if (editing) {
      onUpdate(editing.expenseId, {
        ...money,
        // 보낸 것만 바뀐다 — 비우는 것은 clear로 말해야 한다.
        ...(title.trim() === ""
          ? { clearTitle: true }
          : { title: title.trim() }),
        ...(category === null ? { clearCategory: true } : { category }),
        ...(label === ""
          ? { clearPaymentMethod: true }
          : { paymentMethod: label }),
        // 원화로 되돌리는 길. amount는 위에서 이미 실었다.
        ...(foreign ? {} : { clearFx: true }),
      });
    } else {
      onCreate({
        occurredOn,
        ...money,
        ...(title.trim() === "" ? {} : { title: title.trim() }),
        ...(category === null ? {} : { category }),
        ...(label === "" ? {} : { paymentMethod: label }),
      });
    }
    rememberLastPaymentMethod(tripId, label);
  };

  return (
    <BottomSheet
      open={open}
      onOpenChange={onOpenChange}
      title={editing ? "지출 고치기" : "지출 적기"}
      description={describe(editing, cityName, occurredOn)}
    >
      <div className="flex flex-col gap-4">
        <div className="flex items-center gap-2">
          <Input
            value={amount}
            inputMode="numeric"
            autoFocus
            aria-label="금액"
            onChange={(event) =>
              setAmount(event.currentTarget.value.replace(/[^0-9]/g, ""))
            }
            className="h-13 flex-1 text-[28px] font-semibold tabular-nums"
          />
          {[cityCurrency, KRW]
            .filter((code, index, all) => code && all.indexOf(code) === index)
            .map((code) => (
              <Chip
                key={code}
                selected={currency === code}
                onClick={() => setPickedCurrency(code as string)}
              >
                {code === KRW ? "KRW ₩" : code === "JPY" ? "JPY ¥" : code}
              </Chip>
            ))}
        </div>

        {/* 환산 줄이 「굳는다」고 말하는 이유 — 다녀온 여행의 총액이 매일 바뀌면 안 된다. */}
        <p className="text-muted-foreground text-[13px] tabular-nums">
          {krw === null
            ? "금액을 적으면 원화가 여기 나와요"
            : `${formatAmount(krw)}원 · 오늘 환율로 굳습니다`}
        </p>

        {/*
          이름과 곁들임말을 한 덩이로 묶는다 — `flex-col` 안에서 둘을 나란히 두면
          곁들임말이 제 줄로 떨어져 나가, 옆의 분류 줄과 높이가 어긋난다.
        */}
        <label className="flex flex-col gap-2 text-[13px]">
          <span>
            무엇에{" "}
            <span className="text-muted-foreground">— 안 적어도 돼요</span>
          </span>
          <Input
            value={title}
            aria-label="무엇에"
            placeholder="이자카야"
            onChange={(event) => setTitle(event.currentTarget.value)}
          />
        </label>

        <Field label="분류" hint="— 나중에 채워도 돼요">
          {EXPENSE_CATEGORIES.map((code) => (
            <Chip
              key={code}
              selected={category === code}
              onClick={() => setCategory(category === code ? null : code)}
            >
              {EXPENSE_CATEGORY_LABELS[code]}
            </Chip>
          ))}
        </Field>

        <label className="flex flex-col gap-2 text-[13px]">
          <span>
            결제수단{" "}
            <span className="text-muted-foreground">— 직전에 쓴 것</span>
          </span>
          <Input
            value={paymentMethod}
            aria-label="결제수단"
            placeholder="국민 체크"
            list={listId}
            maxLength={40}
            onChange={(event) => setPaymentMethod(event.currentTarget.value)}
          />
          {/*
            후보는 이 여행에서 이미 쓴 값뿐이다. 전역 목록을 만들면 그게 자산 테이블이고,
            그때부터 이름 고치기·합치기·숨기기가 따라온다(§4.2).
          */}
          <datalist id={listId}>
            {paymentMethods.map((name) => (
              <option key={name} value={name} />
            ))}
          </datalist>
        </label>

        <div className="flex items-center gap-2">
          {editing && (
            <Button
              type="button"
              variant="outline"
              className="h-11"
              disabled={pending}
              onClick={() => onDelete(editing)}
            >
              지우기
            </Button>
          )}
          <Button
            type="button"
            className="h-11 flex-1"
            disabled={!valid || pending}
            onClick={save}
          >
            저장
          </Button>
        </div>
      </div>
    </BottomSheet>
  );
}

/** 「오사카 · 10.26 (월) · 30초 안에 끝나게」. 모르는 값은 조용히 뺀다. */
function describe(
  editing: ExpenseRow | null,
  cityName: string | null,
  date: string | null,
): string {
  if (editing) {
    return formatDateWithWeekday(editing.occurredOn);
  }
  return [
    cityName,
    date === null ? null : formatDateWithWeekday(date),
    "30초 안에 끝나게",
  ]
    .filter(Boolean)
    .join(" · ");
}

/**
 * 외화 → 원화. 환율을 아직 못 받았거나 고시에 없으면 <b>보여줄 값이 없다</b> —
 * 지어내지 않는다. 그 경우 환산 줄은 「금액을 적으면…」으로 남고, 저장은 그대로 된다
 * (서버가 저장 시점에 다시 채운다).
 */
function convertedKrw(
  amount: number,
  rate: number | null | undefined,
): number | null {
  return rate == null ? null : Math.round(amount * rate);
}

function Field({
  label,
  hint,
  children,
}: {
  label: string;
  hint: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-2">
      <p className="text-[13px]">
        {label} <span className="text-muted-foreground">{hint}</span>
      </p>
      <div className="flex flex-wrap gap-2">{children}</div>
    </div>
  );
}

function Chip({
  selected,
  onClick,
  children,
}: {
  selected: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onClick}
      className={cn(
        "min-h-9 rounded-full border px-3.5 py-2 text-sm",
        selected
          ? "border-primary bg-primary/10 text-primary font-semibold"
          : "border-border bg-background text-muted-foreground",
      )}
    >
      {children}
    </button>
  );
}
