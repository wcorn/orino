import { TriangleAlert } from "lucide-react";
import { useState } from "react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { UpcomingItem } from "@/features/ledger/api/ledger";
import {
  useConfirmOccurrence,
  useOccurrenceAction,
} from "@/features/ledger/hooks/useLedgerMutations";
import { formatAmount, formatDateHeader } from "@/features/ledger/lib/money";

/**
 * 미납 상시 경고.
 *
 * <p><b>dismiss 버튼을 만들지 않는다.</b> 액션은 「확정」과 「건너뛰기」 둘뿐이고, 확정하거나
 * 건너뛰어야만 사라진다 — <b>눈에 거슬리는 게 목적이다</b>(확정 명세 §6.4).
 *
 * <p>「무시」에 해당하는 행위는 서버에도 없다. 화면에서만 숨길 수 있게 두면 안 낸 돈이
 * 안 낸 채로 잊힌다.
 */
export function OverdueAlert({ items }: { items: UpcomingItem[] }) {
  const overdue = items.filter((item) => item.overdue);
  if (overdue.length === 0) {
    return null;
  }
  return (
    <Alert variant="destructive">
      <TriangleAlert />
      <AlertTitle>미납 {overdue.length}건</AlertTitle>
      <AlertDescription>
        <p>
          확정(실제 출금일로 이동)하거나 건너뛰기로 정리해야 사라집니다. 무시할
          수 없어요.
        </p>
        <ul className="mt-2 flex flex-col gap-2">
          {overdue.map((item) => (
            <li key={`${item.recurringId}-${item.occurrenceDate}`}>
              <OverdueRow item={item} />
            </li>
          ))}
        </ul>
      </AlertDescription>
    </Alert>
  );
}

function OverdueRow({ item }: { item: UpcomingItem }) {
  const [actualDate, setActualDate] = useState("");
  const confirm = useConfirmOccurrence();
  const act = useOccurrenceAction();

  // 카드 청구서 미납은 결제 처리 화면에서 낸다 — 여기서 회차를 손댈 수 있는 것은 정기 항목뿐이다.
  if (item.recurringId === null || item.occurrenceDate === null) {
    return (
      <span className="text-[13px]">
        {item.title ?? "미납"} {formatAmount(item.amount)}원 ·{" "}
        {formatDateHeader(item.date)}
      </span>
    );
  }

  const recurringId = item.recurringId;
  const occurrenceDate = item.occurrenceDate;

  return (
    <div className="flex flex-wrap items-center gap-2 text-[13px]">
      <span className="min-w-0 flex-1 truncate">
        {item.title ?? "정기 항목"} {formatAmount(item.amount)}원이{" "}
        {formatDateHeader(item.date)}에 빠지지 않았어요
      </span>
      <Input
        type="date"
        value={actualDate}
        onChange={(event) => setActualDate(event.target.value)}
        aria-label={`${item.title ?? "정기 항목"} 실제 출금일`}
        className="h-8 w-[150px]"
      />
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={actualDate === "" || confirm.isPending}
        onClick={() =>
          confirm.mutate({ recurringId, occurrenceDate, actualDate })
        }
      >
        확정
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        disabled={act.isPending}
        onClick={() =>
          act.mutate({ recurringId, occurrenceDate, action: "SKIP" })
        }
      >
        건너뛰기
      </Button>
    </div>
  );
}
