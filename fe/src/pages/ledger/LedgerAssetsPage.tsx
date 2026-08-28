import { Banknote, CreditCard, Landmark, Plus } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingText } from "@/components/ui/loading-text";
import type { AssetGroupView, AssetView } from "@/features/ledger/api/ledger";
import { useTransactionModal } from "@/features/ledger/components/transactionModalContext";
import { useLedgerAssets } from "@/features/ledger/hooks/useLedgerQueries";
import { formatBalance } from "@/features/ledger/lib/money";
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
  const { openTransactionModal } = useTransactionModal();

  return (
    <div className="mx-auto flex max-w-[880px] flex-col gap-5">
      <PageHeader
        title="자산"
        description="잔액은 저장된 값이 아니라 내역에서 계산한 값이에요"
        actions={
          <Button type="button" onClick={openTransactionModal}>
            <Plus className="size-4" />
            입력 <kbd className="ml-1 text-[11px] opacity-70">N</kbd>
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
    </div>
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
