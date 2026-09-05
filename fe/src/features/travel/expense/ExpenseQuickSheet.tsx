import { useEffect, useMemo, useState } from "react";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useCreateTransaction } from "@/features/ledger/hooks/useLedgerMutations";
import {
  useFxRate,
  useLedgerAssets,
  useLedgerCategories,
} from "@/features/ledger/hooks/useLedgerQueries";
import { formatAmount } from "@/features/ledger/lib/money";
import { cn } from "@/lib/utils";

import { getLastAsset, rememberLastAsset } from "./lastAsset";

/**
 * 여행에서 자주 쓰는 갈래(§6.1). <b>이 이름들이 다 있지는 않다</b> — 가계부 프리셋에는
 * 식비·교통만 있고 관광·쇼핑·숙소는 없다.
 *
 * <p>그렇다고 없는 카테고리를 지어내거나 「관광 → 문화」처럼 짝지어 두지 않는다. 뜻이 다른
 * 것을 같은 것으로 만들면 나중에 통계가 조용히 틀린다. 대신 <b>있는 것을 먼저 놓고
 * 나머지는 사용자의 카테고리로 채운다</b> — 이 줄의 목적은 「대개 여기서 끝난다」이지
 * 「이 다섯 개여야 한다」가 아니다.
 */
const PREFERRED_CATEGORIES = ["식비", "교통", "관광", "쇼핑", "숙소"];
const CHIP_LIMIT = 5;

interface ExpenseQuickSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  tripId: number;
  /** 오늘 있는 도시. 통화 기본값이 여기서 온다 — `trip.currency`는 v2.1에서 사라졌다. */
  cityName: string | null;
  cityCurrency: string | null;
  dayNumber: number | null;
  /** 오늘 날짜(여행 기준). 서버가 준 값을 그대로 쓴다. */
  occurredOn: string;
  onSaved: () => void;
}

/**
 * 지출 빠른 입력(화면 §10.3 · 명세 §6.1).
 *
 * <p><b>30초 안에 끝나야 한다.</b> 그래서 금액만 적고 저장할 수 있다 — 카테고리를 고르느라
 * 기록을 포기하느니 나중에 채운다. 안 채운 것은 경비 화면에 「정리할 내역 N건」으로 남는다.
 *
 * <p>저장은 <b>가계부 API</b>로 나간다({@code POST /api/ledger/transactions}). 여행 전용 지출
 * 엔드포인트를 만들지 않는다 — 원장은 하나뿐이고 여행은 그 위의 읽기 뷰다.
 *
 * <p>기본값은 전부 FE가 정한다. 통화는 오늘 도시, 결제수단은 직전에 쓴 것, 날짜와 N일차는
 * 자동이다 — 사용자가 고르는 것은 금액 하나로 줄인다.
 */
export function ExpenseQuickSheet({
  open,
  onOpenChange,
  tripId,
  cityName,
  cityCurrency,
  dayNumber,
  occurredOn,
  onSaved,
}: ExpenseQuickSheetProps) {
  const [amount, setAmount] = useState("");
  const [currency, setCurrency] = useState("KRW");
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [assetId, setAssetId] = useState<number | null>(null);

  const { data: assetData } = useLedgerAssets(open);
  const { data: categories } = useLedgerCategories("EXPENSE");
  const createTransaction = useCreateTransaction();

  const assets = useMemo(
    () => (assetData?.groups ?? []).flatMap((group) => group.assets),
    [assetData],
  );
  const chips = useMemo(() => {
    const all = categories ?? [];
    const preferred = PREFERRED_CATEGORIES.map((name) =>
      all.find((category) => category.name === name),
    ).filter((category) => category !== undefined);
    // 남는 자리는 사용자의 카테고리로 채운다. 하위 카테고리는 넣지 않는다 —
    // 한 손으로 고르는 줄에 「식비 > 카페」까지 늘어놓으면 고르는 데 30초가 간다.
    const rest = all.filter(
      (category) =>
        category.parentId === null &&
        !preferred.some((chip) => chip.id === category.id),
    );
    return [...preferred, ...rest].slice(0, CHIP_LIMIT);
  }, [categories]);

  // 열 때마다 기본값으로 되돌린다. 남아 있으면 방금 저장한 금액이 다음 입력에 얹혀 보인다.
  useEffect(() => {
    if (!open) return;
    setAmount("");
    setCategoryId(null);
    // 오사카면 엔, 인천공항이면 원. 도시가 없으면(기간 밖) 원화로 둔다.
    setCurrency(cityCurrency ?? "KRW");
    setAssetId(getLastAsset(tripId));
  }, [open, cityCurrency, tripId]);

  // 결제수단을 한 번도 고른 적 없으면 첫 자산으로 시작한다 — 자산은 반드시 있어야 저장된다.
  useEffect(() => {
    if (open && assetId === null && assets.length > 0) {
      setAssetId(assets[0].id);
    }
  }, [open, assetId, assets]);

  const foreign = currency !== "KRW";
  // 외화일 때만 환율을 부른다. 원화 입력에 환율 요청이 붙으면 그건 그냥 낭비다.
  const { data: fx } = useFxRate(open && foreign ? currency : null);

  const parsed = amount.trim() === "" ? null : Number(amount);
  const krw =
    parsed === null ? null : foreign ? convertedKrw(parsed, fx?.rate) : parsed;

  const save = () => {
    if (parsed === null || parsed <= 0 || assetId === null) return;
    createTransaction.mutate(
      {
        type: "EXPENSE",
        occurredOn,
        assetId,
        categoryId,
        tripId,
        // 원화면 amount를, 외화면 fx를 보낸다. 환율은 서버가 오늘 고시로 채우고
        // 그 거래에 고정한다 — 조회할 때마다 다시 계산하면 총액이 매일 바뀐다(§4.3).
        ...(foreign
          ? { fx: { currency, amount: parsed, rate: null } }
          : { amount: parsed }),
      },
      {
        onSuccess: () => {
          rememberLastAsset(tripId, assetId);
          onOpenChange(false);
          onSaved();
        },
      },
    );
  };

  return (
    <BottomSheet
      open={open}
      onOpenChange={onOpenChange}
      title="지출 적기"
      description={describe(cityName, dayNumber)}
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
          {[cityCurrency, "KRW"]
            .filter((code, index, all) => code && all.indexOf(code) === index)
            .map((code) => (
              <Chip
                key={code}
                selected={currency === code}
                onClick={() => setCurrency(code as string)}
              >
                {code === "KRW" ? "KRW ₩" : code === "JPY" ? "JPY ¥" : code}
              </Chip>
            ))}
        </div>

        {/* 환산 줄이 「굳는다」고 말하는 이유 — 다녀온 여행의 총액이 매일 바뀌면 안 된다. */}
        <p className="text-muted-foreground text-[13px] tabular-nums">
          {krw === null
            ? "금액을 적으면 원화가 여기 나와요"
            : `${formatAmount(krw)}원 · 오늘 환율로 굳습니다`}
        </p>

        <Field label="카테고리" hint="— 나중에 채워도 돼요">
          {chips.map((category) => (
            <Chip
              key={category.id}
              selected={categoryId === category.id}
              onClick={() =>
                setCategoryId(categoryId === category.id ? null : category.id)
              }
            >
              {category.name}
            </Chip>
          ))}
        </Field>

        <Field label="결제수단" hint="— 직전에 쓴 것">
          {assets.map((asset) => (
            <Chip
              key={asset.id}
              selected={assetId === asset.id}
              onClick={() => setAssetId(asset.id)}
            >
              {asset.name}
            </Chip>
          ))}
        </Field>

        <Button
          type="button"
          className="h-11"
          disabled={
            parsed === null ||
            parsed <= 0 ||
            assetId === null ||
            createTransaction.isPending
          }
          onClick={save}
        >
          저장
        </Button>
      </div>
    </BottomSheet>
  );
}

/** 「오사카 · 4일차 · 30초 안에 끝나게」. 모르는 값은 조용히 뺀다. */
function describe(cityName: string | null, dayNumber: number | null): string {
  return [
    cityName,
    dayNumber === null ? null : `${dayNumber}일차`,
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
