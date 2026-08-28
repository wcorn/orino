import { useState } from "react";

import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";

import { useReconcileAsset } from "../hooks/useLedgerMutations";
import { formatBalance } from "../lib/money";
import { todayIso } from "../lib/period";

interface ReconcileModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  assetId: number;
  assetName: string;
  /** 원장에서 파생한 현재 잔액. 사용자가 실제 잔액과 견줄 기준이다. */
  derivedBalance: number;
}

/**
 * 잔액 맞추기(`LDG-004`).
 *
 * <p>잔액을 컬럼으로 저장하지 않기로 한 이상(D-8) 어긋남은 반드시 생긴다 — 현금을 쓰고 안 적거나,
 * 자동이체를 빠뜨리거나. <b>고칠 길이 없으면 사람은 가계부를 버린다.</b>
 *
 * <p>차액은 「조정」 거래로 남는다. 조용히 잔액만 바꾸지 않는 이유는, 그 순간부터 원장과
 * 잔액이 다른 이야기를 하기 시작하기 때문이다.
 */
export function ReconcileModal({
  open,
  onOpenChange,
  assetId,
  assetName,
  derivedBalance,
}: ReconcileModalProps) {
  const [actual, setActual] = useState("");
  const [memo, setMemo] = useState("");
  const reconcile = useReconcileAsset();

  const parsed = actual.trim() === "" ? null : Number(actual.replace(/,/g, ""));
  const difference =
    parsed === null || Number.isNaN(parsed) ? null : parsed - derivedBalance;

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (parsed === null || Number.isNaN(parsed)) {
      return;
    }
    reconcile.mutate(
      {
        id: assetId,
        body: {
          actualBalance: parsed,
          occurredOn: todayIso(),
          memo: memo.trim() || null,
        },
      },
      {
        onSuccess: () => {
          setActual("");
          setMemo("");
          onOpenChange(false);
        },
      },
    );
  };

  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      title="잔액 맞추기"
      description={`${assetName}의 실제 잔액을 넣으면 차액을 「조정」 거래로 남깁니다.`}
    >
      <form onSubmit={submit} className="mt-4 flex flex-col gap-4">
        <div className="bg-muted flex items-center justify-between rounded-lg px-4 py-2.5 text-sm">
          <span className="text-muted-foreground">원장 잔액</span>
          <span className="tabular-nums">{formatBalance(derivedBalance)}</span>
        </div>

        <FormField label="실제 잔액" htmlFor="ledger-actual-balance">
          <Input
            id="ledger-actual-balance"
            inputMode="numeric"
            autoComplete="off"
            autoFocus
            value={actual}
            onChange={(event) => setActual(event.target.value)}
            placeholder="통장에 찍힌 금액"
            className="tabular-nums"
          />
        </FormField>

        {difference !== null && (
          <p className="text-muted-foreground text-[13px] tabular-nums">
            {difference === 0
              ? "이미 맞아요 — 조정 거래를 만들지 않습니다."
              : `차이 ${formatBalance(difference)} — 이만큼을 조정 거래로 남깁니다.`}
          </p>
        )}

        <FormField label="메모" htmlFor="ledger-reconcile-memo">
          <Input
            id="ledger-reconcile-memo"
            autoComplete="off"
            value={memo}
            onChange={(event) => setMemo(event.target.value)}
            placeholder="ATM 확인"
          />
        </FormField>

        <Modal.Footer>
          <Button
            type="button"
            variant="ghost"
            onClick={() => onOpenChange(false)}
          >
            취소
          </Button>
          <Button
            type="submit"
            disabled={difference === null || reconcile.isPending}
          >
            맞추기
          </Button>
        </Modal.Footer>
      </form>
    </Modal>
  );
}
