import { useEffect, useState } from "react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";

interface BudgetModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 지금 정해진 예산. 안 정했으면 `null`이다. */
  current: number | null;
  onSave: (amount: number | null) => void;
  pending: boolean;
}

/**
 * 예산 설정(화면 §10.4).
 *
 * <p><b>총액 하나만 받는다.</b> 도시별·카테고리별로 나누지 않는다 — 여행을 한 번도 안
 * 다녀왔는데 도시마다 예산을 걸면 지키지도 않을 숫자를 여섯 개 관리하게 된다(§5.1).
 *
 * <p>「월 예산과 섞이지 않아요」를 여기서 미리 말한다. 여행 지출이 월 예산 게이지에 들어가면
 * 여행 간 달은 <b>항상 초과</b>가 되고, 그러면 그 게이지는 아무것도 알려주지 않는다(§5.2).
 */
export function BudgetModal({
  open,
  onOpenChange,
  current,
  onSave,
  pending,
}: BudgetModalProps) {
  const [amount, setAmount] = useState("");

  // 열 때마다 지금 값으로 되돌린다. 남아 있으면 지난번에 쓰다 만 숫자가 얹혀 보인다.
  useEffect(() => {
    if (open) {
      setAmount(current === null ? "" : String(current));
    }
  }, [open, current]);

  const parsed = amount.trim() === "" ? null : Number(amount);

  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      title="여행 예산"
      description="이번 여행에 쓸 총액 하나만 정합니다. 도시별·카테고리별로 나누지 않아요."
      size="sm"
    >
      <div className="flex flex-col gap-4">
        <div className="flex items-center gap-2">
          <Input
            value={amount}
            inputMode="numeric"
            aria-label="예산 총액"
            placeholder="비우면 예산을 지웁니다"
            onChange={(event) =>
              setAmount(event.currentTarget.value.replace(/[^0-9]/g, ""))
            }
            className="h-11 text-xl font-semibold tabular-nums"
          />
          <span className="text-muted-foreground text-sm">원</span>
        </div>

        <Alert variant="info">
          <AlertTitle>월 예산과 섞이지 않아요</AlertTitle>
          <AlertDescription>
            여행 지출은 월 예산 게이지에 들어가지 않습니다. 대신 월 예산 화면에
            「이 달 여행으로 41만」을 한 줄로 남겨요.
          </AlertDescription>
        </Alert>
      </div>

      <Modal.Footer
        submitLabel="저장"
        pending={pending}
        // 0은 「안 정함」과 구분되지 않는다 — 서버도 400으로 거절하지만,
        // 저장을 누른 뒤에 듣는 것과 누르기 전에 아는 것은 다르다(§5.3).
        submitDisabled={parsed !== null && parsed <= 0}
        onSubmit={() => onSave(parsed)}
      />
    </Modal>
  );
}
