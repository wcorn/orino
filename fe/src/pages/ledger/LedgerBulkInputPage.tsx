import { Info, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { PageHeader } from "@/components/PageHeader";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import type {
  LedgerFlow,
  TransactionCreateRequest,
} from "@/features/ledger/api/ledger";
import { useBulkCreateTransactions } from "@/features/ledger/hooks/useLedgerMutations";
import {
  useLedgerAssets,
  useLedgerCategories,
} from "@/features/ledger/hooks/useLedgerQueries";
import { evaluate } from "@/features/ledger/lib/calculator";
import { formatAmount } from "@/features/ledger/lib/money";
import { todayIso } from "@/features/ledger/lib/period";

interface Row {
  key: number;
  occurredOn: string;
  amount: string;
  assetId: string;
  categoryId: string;
  title: string;
}

let nextKey = 0;

function emptyRow(assetId: string): Row {
  return {
    key: nextKey++,
    occurredOn: todayIso(),
    amount: "",
    assetId,
    categoryId: "",
    title: "",
  };
}

/**
 * 다건 입력 `/ledger/transactions/bulk` (`LDG-015`).
 *
 * <p>카드 명세서를 보며 <b>몰아 적을 때</b> 쓴다. 가져오기(#1268)와 겹치지 않는다 —
 * 그건 파일이 있을 때고, 이건 화면을 보며 손으로 옮길 때다. 명세서 PDF처럼 파일로 못 받는
 * 경우가 실제로 많다.
 *
 * <p>저장은 <b>전부-아니면-전무</b>다. 서버가 한 트랜잭션으로 처리하므로 「7건은 됐고 3건은
 * 실패」로 끝나지 않는다 — 그러면 어디까지 옮겼는지 사람이 다시 맞춰야 하고, 몰아 적는 이유가
 * 없어진다.
 */
export function LedgerBulkInputPage() {
  const navigate = useNavigate();
  const { data: assetList } = useLedgerAssets();
  const { data: categories } = useLedgerCategories("EXPENSE");
  const bulk = useBulkCreateTransactions();

  const assets = (assetList?.groups ?? [])
    .flatMap((group) => group.assets)
    .filter((asset) => !asset.hidden);
  const defaultAsset = assets[0] ? String(assets[0].id) : "";

  const [rows, setRows] = useState<Row[]>([emptyRow("")]);

  // 자산이 늦게 도착하면 첫 줄이 비어 있다 — 그때 한 번 채운다.
  const filled = rows.map((row) => ({
    ...row,
    assetId: row.assetId || defaultAsset,
  }));

  const patch = (key: number, next: Partial<Row>) =>
    setRows((prev) =>
      prev.map((row) => (row.key === key ? { ...row, ...next } : row)),
    );

  const total = filled.reduce(
    (sum, row) => sum + (evaluate(row.amount) ?? 0),
    0,
  );
  const ready = filled.filter((row) => (evaluate(row.amount) ?? 0) > 0);

  const submit = () => {
    const transactions: TransactionCreateRequest[] = ready.map((row) => ({
      type: "EXPENSE" as LedgerFlow,
      amount: evaluate(row.amount) as number,
      occurredOn: row.occurredOn,
      assetId: Number(row.assetId),
      categoryId: row.categoryId ? Number(row.categoryId) : null,
      title: row.title.trim() || null,
    }));
    bulk.mutate(transactions, {
      onSuccess: () => navigate("/ledger/transactions"),
    });
  };

  return (
    <div className="mx-auto flex max-w-[880px] flex-col gap-4">
      <PageHeader
        title="여러 건 적기"
        description="카드 명세서를 보며 줄 단위로 옮겨 적어요"
        actions={
          <Button
            type="button"
            variant="ghost"
            render={<Link to="/ledger/transactions" />}
          >
            내역으로
          </Button>
        }
      />

      {/*
        아이콘을 직계 자식으로 둔다. `Alert`는 svg가 없으면 첫 트랙이 0폭이라(`grid-cols-[0_1fr]`)
        한글이 한 글자씩 세로로 쪼개진다 — 실제로 그렇게 깨졌다(화면 설계 §0).
      */}
      <Alert variant="info">
        <Info />
        <AlertTitle>한 줄이라도 잘못되면 전부 저장하지 않아요</AlertTitle>
        <AlertDescription>
          어디까지 옮겼는지 다시 맞추지 않아도 되도록 한 번에 넣습니다.
        </AlertDescription>
      </Alert>

      <div className="flex flex-col gap-2">
        {filled.map((row) => (
          <div
            key={row.key}
            className="grid grid-cols-1 gap-2 md:grid-cols-[132px_minmax(0,1fr)_120px_150px_150px_40px]"
          >
            <Input
              type="date"
              aria-label="날짜"
              value={row.occurredOn}
              onChange={(event) =>
                patch(row.key, { occurredOn: event.target.value })
              }
            />
            <Input
              aria-label="내용"
              placeholder="가맹점"
              value={row.title}
              onChange={(event) =>
                patch(row.key, { title: event.target.value })
              }
            />
            <Input
              aria-label="금액"
              inputMode="decimal"
              placeholder="0"
              className="tabular-nums"
              value={row.amount}
              onChange={(event) =>
                patch(row.key, { amount: event.target.value })
              }
            />
            <Select
              value={row.assetId}
              onValueChange={(value) => patch(row.key, { assetId: value })}
              ariaLabelledby="bulk-asset-label"
              options={assets.map((asset) => ({
                value: String(asset.id),
                label: asset.name,
              }))}
            />
            <Select
              value={row.categoryId}
              onValueChange={(value) => patch(row.key, { categoryId: value })}
              ariaLabelledby="bulk-category-label"
              options={[
                { value: "", label: "선택 안 함" },
                ...(categories ?? []).map((category) => ({
                  value: String(category.id),
                  label: category.name,
                })),
              ]}
            />
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              aria-label="줄 삭제"
              // 마지막 한 줄은 남긴다 — 빈 화면에서는 다시 시작할 방법이 없다.
              disabled={filled.length === 1}
              onClick={() =>
                setRows((prev) => prev.filter((item) => item.key !== row.key))
              }
            >
              <Trash2 className="size-4" />
            </Button>
          </div>
        ))}
      </div>
      <span id="bulk-asset-label" className="sr-only">
        자산
      </span>
      <span id="bulk-category-label" className="sr-only">
        카테고리
      </span>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <Button
          type="button"
          variant="outline"
          onClick={() => setRows((prev) => [...prev, emptyRow(defaultAsset)])}
        >
          <Plus className="size-4" />줄 추가
        </Button>

        <div className="flex items-center gap-4">
          {/* 저장 전에 합계를 보여준다 — 명세서 총액과 맞춰 보는 것이 이 화면의 마지막 확인이다. */}
          <span className="text-sm tabular-nums">
            {ready.length}건 · 합계 {formatAmount(total)}
          </span>
          <Button
            type="button"
            disabled={ready.length === 0 || bulk.isPending}
            onClick={submit}
          >
            전부 저장
          </Button>
        </div>
      </div>
    </div>
  );
}
