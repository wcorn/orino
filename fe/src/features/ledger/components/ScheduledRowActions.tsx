import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { UpcomingItem } from "@/features/ledger/api/ledger";
import {
  useDeleteTransaction,
  useOccurrenceAction,
  useUpdateTransaction,
} from "@/features/ledger/hooks/useLedgerMutations";

type Mode = "AMOUNT" | "MOVE" | null;

/**
 * 예정 줄의 인라인 액션 — `금액 수정` `건너뛰기` `날짜 변경`.
 *
 * <p>예정을 고치려고 모달을 열게 하지 않는다. 「이번 달만 17,000원」은 <b>그 줄에서</b>
 * 끝나야 하는 일이고, 규칙을 고치는 것과는 다른 행위다(확정 명세 §6.5).
 *
 * <p>손대는 대상이 둘이다. 정기 회차는 <b>그 회차만</b> override 1행으로 남고, 직접 예약은
 * 원장에 이미 있는 행을 고친다 — 같은 버튼이지만 가는 곳이 다르다.
 */
export function ScheduledRowActions({ item }: { item: UpcomingItem }) {
  const [mode, setMode] = useState<Mode>(null);
  const [value, setValue] = useState("");
  const occurrence = useOccurrenceAction();
  const update = useUpdateTransaction();
  const remove = useDeleteTransaction();

  const isRecurring = item.recurringId !== null && item.occurrenceDate !== null;
  // 카드 대금과 할부는 여기서 손대지 않는다 — 청구서 쪽에서 정산할 일이다.
  if (!isRecurring && item.transactionId === null) {
    return null;
  }

  const submit = () => {
    if (mode === "AMOUNT") {
      const amount = Number(value.replace(/[^0-9]/g, ""));
      if (!Number.isFinite(amount) || amount <= 0) {
        return;
      }
      if (isRecurring) {
        occurrence.mutate({
          recurringId: item.recurringId as number,
          occurrenceDate: item.occurrenceDate as string,
          action: "AMOUNT",
          amount,
        });
      } else {
        update.mutate({ id: item.transactionId as number, body: { amount } });
      }
    }
    if (mode === "MOVE") {
      if (value === "") {
        return;
      }
      if (isRecurring) {
        occurrence.mutate({
          recurringId: item.recurringId as number,
          occurrenceDate: item.occurrenceDate as string,
          action: "MOVE",
          movedTo: value,
        });
      } else {
        update.mutate({
          id: item.transactionId as number,
          body: { occurredOn: value },
        });
      }
    }
    setMode(null);
    setValue("");
  };

  if (mode !== null) {
    return (
      <span className="flex items-center gap-1">
        <Input
          autoFocus
          type={mode === "AMOUNT" ? "text" : "date"}
          inputMode={mode === "AMOUNT" ? "numeric" : undefined}
          value={value}
          onChange={(event) => setValue(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              submit();
            }
            if (event.key === "Escape") {
              setMode(null);
            }
          }}
          aria-label={mode === "AMOUNT" ? "이번 회차 금액" : "옮길 날짜"}
          className="h-7 w-[128px]"
        />
        <Button type="button" size="xs" onClick={submit}>
          적용
        </Button>
      </span>
    );
  }

  return (
    <span className="flex items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100">
      <Button
        type="button"
        variant="ghost"
        size="xs"
        onClick={() => {
          setMode("AMOUNT");
          setValue(String(item.amount));
        }}
      >
        금액 수정
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="xs"
        onClick={() => {
          if (isRecurring) {
            occurrence.mutate({
              recurringId: item.recurringId as number,
              occurrenceDate: item.occurrenceDate as string,
              action: "SKIP",
            });
          } else {
            remove.mutate(item.transactionId as number);
          }
        }}
      >
        건너뛰기
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="xs"
        onClick={() => {
          setMode("MOVE");
          setValue(item.date);
        }}
      >
        날짜 변경
      </Button>
    </span>
  );
}
