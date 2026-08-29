import { Hand } from "lucide-react";
import { useState } from "react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Modal } from "@/components/ui/modal";
import type { AssetView, StatementView } from "@/features/ledger/api/ledger";
import { usePayStatement } from "@/features/ledger/hooks/useLedgerMutations";
import { formatAmount } from "@/features/ledger/lib/money";
import { cn } from "@/lib/utils";

type Method = "FULL" | "PARTIAL";

interface StatementPayModalProps {
  statement: StatementView | null;
  assets: AssetView[];
  defaultAssetId: number | null;
  onClose: () => void;
}

/**
 * 결제 처리 모달.
 *
 * <p><b>왜 자동이 아닌지를 화면이 먼저 말한다.</b> 「왜 매번 눌러야 하나」는 사람이 반드시
 * 묻는 질문이고, 답하지 않으면 다음 버전에서 누군가 자동 기록을 넣는다(확정 명세 §7.2).
 *
 * <p>일부 결제의 이월은 <b>지출이 아니다</b>. 이미 쓸 때 잡혔고 갚는 행위는 지출이 아니다 —
 * 새 지출이 되는 것은 리볼빙 수수료뿐이고, 화면이 그 구분을 그 자리에서 말한다.
 */
export function StatementPayModal({
  statement,
  assets,
  defaultAssetId,
  onClose,
}: StatementPayModalProps) {
  const [method, setMethod] = useState<Method>("FULL");
  const [amount, setAmount] = useState("");
  const [assetId, setAssetId] = useState<number | null>(null);
  const [paidOn, setPaidOn] = useState("");
  const pay = usePayStatement();

  if (statement === null) {
    return null;
  }

  const remaining = statement.breakdown.remaining;
  const partialAmount = Number(amount.replace(/[^0-9]/g, "")) || 0;
  const carried =
    method === "PARTIAL" ? Math.max(remaining - partialAmount, 0) : 0;
  const chosenAsset = assetId ?? defaultAssetId;

  const submit = () => {
    pay.mutate(
      {
        statementId: statement.id,
        body: {
          amount: method === "FULL" ? null : partialAmount,
          paymentAssetId: chosenAsset,
          paidOn: paidOn === "" ? null : paidOn,
        },
      },
      { onSuccess: () => close() },
    );
  };

  const close = () => {
    setMethod("FULL");
    setAmount("");
    setAssetId(null);
    setPaidOn("");
    onClose();
  };

  return (
    <Modal
      open
      onOpenChange={(next) => {
        if (!next) {
          close();
        }
      }}
      size="lg"
      title="결제 처리"
      description={`${statement.paymentDate} 결제일 · 남은 청구액 ${formatAmount(remaining)}`}
    >
      <div className="flex flex-col gap-4">
        <Alert>
          <Hand />
          <AlertTitle>카드 대금은 자동으로 적지 않습니다</AlertTitle>
          <AlertDescription>
            <p>
              잔고 부족·리볼빙·선결제·연회비 때문에 실제 출금액이 예상과 다른
              일이 잦아서요. 모르는 걸 아는 척 적어두면 원장이 조용히
              틀어집니다.
            </p>
          </AlertDescription>
        </Alert>

        <fieldset className="flex flex-col gap-2">
          <legend className="text-[13px] font-medium">결제 방식</legend>
          <div className="grid gap-2 sm:grid-cols-2">
            <MethodCard
              label="전액"
              hint={`${formatAmount(remaining)} 전부`}
              selected={method === "FULL"}
              onSelect={() => setMethod("FULL")}
            />
            <MethodCard
              label="일부"
              hint="실제 출금된 금액만"
              selected={method === "PARTIAL"}
              onSelect={() => setMethod("PARTIAL")}
            />
          </div>
        </fieldset>

        {method === "PARTIAL" && (
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="pay-amount">결제 금액</Label>
            <Input
              id="pay-amount"
              inputMode="numeric"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              placeholder={String(remaining)}
            />
          </div>
        )}

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="pay-asset">출금 계좌</Label>
          <select
            id="pay-asset"
            value={chosenAsset ?? ""}
            onChange={(event) => setAssetId(Number(event.target.value))}
            className="border-input bg-background h-9 rounded-md border px-3 text-sm"
          >
            {assets.map((asset) => (
              <option key={asset.id} value={asset.id}>
                {asset.name}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="pay-date">결제일 (실제 출금일)</Label>
          <Input
            id="pay-date"
            type="date"
            value={paidOn}
            onChange={(event) => setPaidOn(event.target.value)}
          />
          <p className="text-muted-foreground text-[13px]">
            비우면 청구서의 결제일로 적습니다. 다른 날 빠졌다면 그날이 맞아요.
          </p>
        </div>

        {/* 이월은 지출이 아니다. 이 문장이 없으면 다음 달 지출이 부풀어 보인다. */}
        {method === "PARTIAL" && carried > 0 && (
          <Alert variant="warning">
            <AlertTitle>
              {formatAmount(carried)}이 다음 청구서로 이월됩니다
            </AlertTitle>
            <AlertDescription>
              <p>
                이월 자체는 지출이 아니고, 리볼빙 수수료만 새 지출입니다. 남은
                금액은 부채로 계속 잡혀요.
              </p>
            </AlertDescription>
          </Alert>
        )}

        <p className="text-muted-foreground text-[13px]">
          결제 시 생성되는 이체는 지출로 계상하지 않습니다.
        </p>

        <Modal.Footer>
          <Button type="button" variant="ghost" onClick={close}>
            취소
          </Button>
          <Button
            type="button"
            onClick={submit}
            disabled={
              pay.isPending || (method === "PARTIAL" && partialAmount <= 0)
            }
          >
            결제 처리
          </Button>
        </Modal.Footer>
      </div>
    </Modal>
  );
}

function MethodCard({
  label,
  hint,
  selected,
  onSelect,
}: {
  label: string;
  hint: string;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onSelect}
      className={cn(
        "flex flex-col items-start gap-0.5 rounded-lg border p-3 text-left transition-colors",
        selected
          ? "border-primary bg-primary/5"
          : "border-border hover:bg-muted",
      )}
    >
      <span className="text-sm font-medium">{label}</span>
      <span className="text-muted-foreground text-[13px]">{hint}</span>
    </button>
  );
}
