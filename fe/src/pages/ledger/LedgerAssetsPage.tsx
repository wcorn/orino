import { Banknote, CreditCard, Landmark, Plus } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { LoadingText } from "@/components/ui/loading-text";
import type {
  AssetGroupView,
  AssetView,
  CardView,
} from "@/features/ledger/api/ledger";
import { AssetCreateModal } from "@/features/ledger/components/AssetCreateModal";
import {
  useCreatePoint,
  useDeletePoint,
} from "@/features/ledger/hooks/useLedgerMutations";
import {
  useLedgerAssets,
  useLedgerCards,
  usePoints,
} from "@/features/ledger/hooks/useLedgerQueries";
import { formatAmount, formatBalance } from "@/features/ledger/lib/money";
import { cn } from "@/lib/utils";

const GROUP_ICON = {
  BANK: Landmark,
  CARD_ISSUER: CreditCard,
  ETC: Banknote,
} as const;

/**
 * 자산 `/ledger/assets`.
 *
 * <p>맨 위 세 줄이 이 화면의 요지다 — <b>총자산 · 부채 · 순자산</b>. 순자산만 크게 보여주지
 * 않는 것이 중요하다: "통장에 300만 있는데 카드값이 180만"인 상태가 정직하게 보여야 한다
 * (확정 명세 §5.3).
 *
 * <p>잔액은 <b>서버가 원장에서 파생해 준 값</b>이다(D-8). 화면이 다시 더하지 않는다 —
 * 두 곳에서 계산하면 그 둘이 갈리는 순간 어느 쪽이 맞는지 알 수 없다.
 */
export function LedgerAssetsPage() {
  const { data, isPending, isError } = useLedgerAssets();
  const { data: cards } = useLedgerCards();
  const [createOpen, setCreateOpen] = useState(false);

  return (
    <div className="mx-auto flex max-w-[880px] flex-col gap-5">
      {/* 거래 입력은 `N`이 어디서든 연다. 이 화면의 버튼은 여기서만 할 수 있는 일을 맡는다. */}
      <PageHeader
        title="자산"
        description="잔액은 저장된 값이 아니라 내역에서 계산한 값이에요"
        actions={
          <Button type="button" onClick={() => setCreateOpen(true)}>
            <Plus className="size-4" />
            자산 추가
          </Button>
        }
      />

      {isPending && <LoadingText />}
      {isError && (
        <Alert variant="destructive">자산을 불러오지 못했어요.</Alert>
      )}

      {data && (
        <>
          {/* 세 줄로 나란히 둔다. 순자산 하나만 크게 두면 카드값이 가려진다. */}
          <div className="bg-muted grid grid-cols-1 gap-3 rounded-lg px-4 py-3 sm:grid-cols-3">
            <SummaryLine label="총자산" value={data.totalAssets} />
            <SummaryLine
              label="부채"
              value={data.liabilities}
              // 부채는 이 화면에서 진짜로 위험한 값이다 — 여기에만 destructive를 쓴다.
              tone={data.liabilities > 0 ? "text-destructive" : undefined}
            />
            <SummaryLine label="순자산" value={data.netWorth} />
          </div>

          {/*
            부채가 무엇으로 이뤄졌는지 적는다. 「1,700,500」만 있으면 카드값인지 할부인지
            알 수 없고, 알 수 없으면 줄일 방법도 안 보인다.
          */}
          {data.liabilities > 0 && cards && (
            <p className="text-muted-foreground text-[13px]">
              카드 미결제 {formatAmount(cardUnpaid(cards.cards))}
              {cards.installmentOutstanding > 0 &&
                ` · 할부 잔여 ${formatAmount(cards.installmentOutstanding)}`}
              을 부채로 반영합니다. 할부는 아직 청구되지 않은 회차도 이미 갚기로
              한 돈이에요.
            </p>
          )}

          {data.groups.length === 0 && data.hidden.length === 0 && (
            <EmptyState className="min-h-[30svh]">
              <p className="text-muted-foreground text-sm">
                아직 만든 자산이 없어요. 자산이 있어야 거래를 적을 수 있어요.
              </p>
            </EmptyState>
          )}

          {data.groups.map((group) => (
            <AssetGroup key={group.id ?? "ungrouped"} group={group} />
          ))}

          {data.hidden.length > 0 && (
            <section className="flex flex-col gap-2">
              <h2 className="text-muted-foreground text-[13px] font-semibold">
                숨긴 자산
              </h2>
              {/* 지우지 않고 숨긴다 — 과거 내역이 갈 곳을 잃으면 안 된다. */}
              <p className="text-muted-foreground text-[13px]">
                과거 내역은 그대로 보존됩니다.
              </p>
              <ul className="flex flex-col gap-1">
                {data.hidden.map((asset) => (
                  <li key={asset.id}>
                    <AssetRow asset={asset} muted />
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}

      {/* 자산 목록 밖이다. 안에 두면 언젠가 합계에 섞인다. */}
      <PointSection />

      <AssetCreateModal open={createOpen} onOpenChange={setCreateOpen} />
    </div>
  );
}

/**
 * 포인트·마일리지(`LDG-006`).
 *
 * <p><b>자산 목록에 섞지 않는다.</b> 총자산·순자산·통계 어디에도 들어가지 않는다 — 포인트는
 * 쓸 수 있는 곳이 정해진 외상이지 돈이 아니고, 섞는 순간 「자산이 얼마인가」가 답할 수 없는
 * 질문이 된다. 그래서 합계도 내지 않는다: 「포인트」와 「마일」은 서로 더할 수 없다.
 *
 * <p>적어 두는 이유의 절반은 <b>소멸일</b>이라 D-day를 배지로 세운다. 날짜 계산은 서버가
 * 한다 — 화면이 세면 자정 언저리에 서로 다른 날을 말한다.
 */
function PointSection() {
  const { data: points, isPending } = usePoints();
  const create = useCreatePoint();
  const remove = useDeletePoint();

  const [name, setName] = useState("");
  const [balance, setBalance] = useState("");
  const [expiresOn, setExpiresOn] = useState("");

  const submit = () => {
    if (name.trim() === "") {
      return;
    }
    create.mutate(
      {
        name: name.trim(),
        unit: "포인트",
        balance: balance === "" ? 0 : Number(balance),
        expiresOn: expiresOn === "" ? null : expiresOn,
      },
      {
        onSuccess: () => {
          setName("");
          setBalance("");
          setExpiresOn("");
        },
      },
    );
  };

  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-[13px] font-semibold">포인트·마일리지</h2>
      <p className="text-muted-foreground text-[13px]">
        <b>총자산에 포함되지 않아요.</b> 쓸 수 있는 곳이 정해져 있어 돈과 같이
        셀 수 없습니다 — 소멸일을 놓치지 않으려고 적어 둡니다.
      </p>

      {isPending && <LoadingText />}

      {points && points.length > 0 && (
        <ul className="flex flex-col gap-1">
          {points.map((point) => (
            <li
              key={point.id}
              className="border-border flex items-center justify-between gap-3 border-b py-2 text-sm last:border-b-0"
            >
              <span className="flex min-w-0 items-center gap-2">
                <span className="truncate">{point.name}</span>
                {point.daysLeft !== null && (
                  <Badge variant={point.expiringSoon ? "warning" : "outline"}>
                    {point.daysLeft < 0 ? "소멸됨" : `D-${point.daysLeft}`}
                  </Badge>
                )}
              </span>
              <span className="flex shrink-0 items-center gap-2">
                <span className="tabular-nums">
                  {formatAmount(point.balance)} {point.unit}
                </span>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  aria-label={`${point.name} 지우기`}
                  onClick={() => remove.mutate(point.id)}
                >
                  지우기
                </Button>
              </span>
            </li>
          ))}
        </ul>
      )}

      <div className="flex flex-wrap items-end gap-2">
        <div className="flex min-w-[140px] flex-1 flex-col gap-1.5">
          <Label htmlFor="point-name">이름</Label>
          <Input
            id="point-name"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="네이버페이"
          />
        </div>
        <div className="flex w-[120px] flex-col gap-1.5">
          <Label htmlFor="point-balance">잔액</Label>
          <Input
            id="point-balance"
            inputMode="numeric"
            value={balance}
            onChange={(event) => setBalance(event.target.value)}
          />
        </div>
        <div className="flex w-[150px] flex-col gap-1.5">
          <Label htmlFor="point-expires">소멸일</Label>
          <Input
            id="point-expires"
            type="date"
            value={expiresOn}
            onChange={(event) => setExpiresOn(event.target.value)}
          />
        </div>
        <Button type="button" disabled={name.trim() === ""} onClick={submit}>
          추가
        </Button>
      </div>
    </section>
  );
}

function SummaryLine({
  label,
  value,
  tone,
}: {
  label: string;
  value: number;
  tone?: string;
}) {
  return (
    <div className="flex items-baseline justify-between sm:flex-col sm:items-start sm:gap-0.5">
      <span className="text-muted-foreground text-[13px]">{label}</span>
      <span className={cn("text-heading font-semibold tabular-nums", tone)}>
        {formatBalance(value)}
      </span>
    </div>
  );
}

function AssetGroup({ group }: { group: AssetGroupView }) {
  const Icon = GROUP_ICON[group.kind];
  return (
    <section className="flex flex-col gap-1">
      <div className="bg-muted flex items-center justify-between rounded-lg px-3 py-2">
        <span className="flex items-center gap-2 text-[13px] font-medium">
          <Icon className="size-4 opacity-70" />
          {group.name}
        </span>
        <span className="text-[13px] tabular-nums">
          {formatBalance(group.subtotal)}
        </span>
      </div>
      <ul className="flex flex-col gap-1">
        {group.assets.map((asset) => (
          <li key={asset.id}>
            <AssetRow asset={asset} />
          </li>
        ))}
      </ul>
    </section>
  );
}

/** 행 전체가 상세로 가는 버튼이다 — 좁은 화면에서 작은 링크를 겨냥하게 만들지 않는다. */
function AssetRow({
  asset,
  muted = false,
}: {
  asset: AssetView;
  muted?: boolean;
}) {
  const navigate = useNavigate();
  return (
    <button
      type="button"
      onClick={() => navigate(`/ledger/assets/${asset.id}`)}
      className={cn(
        "hover:bg-muted flex w-full items-center justify-between gap-3 rounded-lg px-3 py-2.5 text-left transition-colors",
        muted && "opacity-60",
      )}
    >
      <span className="flex min-w-0 items-center gap-2">
        <span className="truncate text-sm font-medium">{asset.name}</span>
        {asset.accountLast4 && (
          <span className="text-muted-foreground text-[13px] tabular-nums">
            ···{asset.accountLast4}
          </span>
        )}
        {asset.hidden && <Badge variant="outline">해지</Badge>}
      </span>
      <span className="shrink-0 text-sm tabular-nums">
        <AssetAmount asset={asset} />
      </span>
    </button>
  );
}

/**
 * 자산 한 줄의 오른쪽 숫자.
 *
 * <p>셋이 서로 다른 값이다 — 잔액 / 미결제 사용액(부채) / <b>「잔액 없음」</b>.
 * 체크카드에 0을 적으면 「돈이 없다」로 읽히는데, 사실은 <b>잔액이라는 개념이 없는</b>
 * 자산이다. 돈은 연결 계좌에서 빠진다(D-4).
 */
function AssetAmount({ asset }: { asset: AssetView }) {
  if (asset.unpaidAmount != null) {
    return (
      <span className={cn(asset.unpaidAmount > 0 && "text-destructive")}>
        {formatBalance(-asset.unpaidAmount)}
      </span>
    );
  }
  if (asset.balance != null) {
    return <span>{formatBalance(asset.balance)}</span>;
  }
  return (
    <span className="text-muted-foreground text-[13px]">
      잔액 없음
      {asset.linkedAssetName && ` · ${asset.linkedAssetName}에서 출금`}
    </span>
  );
}

/** 카드별 미결제의 합. 부채가 무엇으로 이뤄졌는지 한 줄로 적기 위한 값이다. */
function cardUnpaid(cards: CardView[]): number {
  return cards.reduce((sum, card) => sum + card.unpaidAmount, 0);
}
