import { useState } from "react";

import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";

import type {
  SubscriptionBaseline,
  SubscriptionBaselineChange,
} from "../api/ledger";
import { useUpdateSubscriptionBaseline } from "../hooks/useLedgerMutations";
import { todayIso } from "../lib/period";

interface SubscriptionBaselineModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  assetId: number;
  /** 지금 적혀 있는 기준값. 없으면 빈 칸으로 연다. */
  baseline: SubscriptionBaseline | null;
  /** 저장 결과. 바꾸기 직전 추정(`before`)이 들어 있어 화면이 차이를 한 번 알린다. */
  onSaved: (change: SubscriptionBaselineChange) => void;
}

/** 숫자만 남긴다. 청약홈에서 복사한 `5,300,000`도 그대로 붙여 넣을 수 있게. */
function digits(value: string): string {
  return value.replace(/\D/g, "");
}

/**
 * 청약홈 기준값 적기(화면 설계 §14.3, D-18).
 *
 * <p><b>기준일이 아니라 「몇 월분까지」를 받는다.</b> 조회한 날짜로 받으면 이번 달 약정일이
 * 지났는지 알 수 없어 그 달을 두 번 세거나 빠뜨린다 — 청약홈이 보여주는 단위를 그대로 받는다.
 *
 * <p>기준값은 돈이 아니다. 잔액을 바꾸지 않고 거래를 만들지 않는다 — 잔액이 어긋났다면 그건
 * 「잔액 맞추기」의 일이다.
 */
export function SubscriptionBaselineModal({
  open,
  onOpenChange,
  assetId,
  baseline,
  onSaved,
}: SubscriptionBaselineModalProps) {
  const thisMonth = todayIso().slice(0, 7);
  const [count, setCount] = useState(baseline ? String(baseline.count) : "");
  const [amount, setAmount] = useState(baseline ? String(baseline.amount) : "");
  // 다시 맞추는 사람은 대개 방금 청약홈을 열어 본 참이다 — 이번 달분에서 시작한다.
  const [throughMonth, setThroughMonth] = useState(thisMonth);
  const update = useUpdateSubscriptionBaseline();

  // 미래 월은 청약홈이 보여줄 수 없는 값이다. 서버도 LDG-ERR-045로 거부한다.
  const monthValid =
    /^\d{4}-\d{2}$/.test(throughMonth) && throughMonth <= thisMonth;
  const canSubmit =
    count !== "" && amount !== "" && monthValid && !update.isPending;

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!canSubmit) {
      return;
    }
    update.mutate(
      {
        id: assetId,
        body: {
          count: Number(count),
          amount: Number(amount),
          throughMonth,
        },
      },
      {
        onSuccess: (change) => {
          onSaved(change);
          onOpenChange(false);
        },
      },
    );
  };

  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      title="청약홈 값 적기"
      description="청약홈 납입 인정 내역에 보이는 값을 그대로 적어 주세요. 잔액은 바뀌지 않아요."
    >
      <form onSubmit={submit} className="mt-4 flex flex-col gap-4">
        <FormField label="인정 회차" htmlFor="ledger-subscription-count">
          <Input
            id="ledger-subscription-count"
            inputMode="numeric"
            autoComplete="off"
            autoFocus
            value={count}
            onChange={(event) => setCount(digits(event.target.value))}
            placeholder="38"
            className="tabular-nums"
          />
        </FormField>

        <FormField label="인정 금액" htmlFor="ledger-subscription-amount">
          <Input
            id="ledger-subscription-amount"
            inputMode="numeric"
            autoComplete="off"
            value={amount}
            onChange={(event) => setAmount(digits(event.target.value))}
            placeholder="5300000"
            className="tabular-nums"
          />
        </FormField>

        <FormField label="몇 월분까지" htmlFor="ledger-subscription-through">
          <Input
            id="ledger-subscription-through"
            type="month"
            max={thisMonth}
            value={throughMonth}
            onChange={(event) => setThroughMonth(event.target.value)}
          />
        </FormField>
        <p className="text-muted-foreground text-[13px]">
          이 다음 달부터는 이 통장으로 들어온 이체를 가계부가 셉니다.
        </p>

        <Modal.Footer>
          <Button
            type="button"
            variant="ghost"
            onClick={() => onOpenChange(false)}
          >
            취소
          </Button>
          <Button type="submit" disabled={!canSubmit}>
            저장
          </Button>
        </Modal.Footer>
      </form>
    </Modal>
  );
}
