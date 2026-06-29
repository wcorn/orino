import { Dialog } from "@base-ui/react/dialog";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/modal";

import type { RoutineScope } from "../../api/routines";

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  mode: "edit" | "delete";
  /** 인스턴스 기준 날짜(YYYY-MM-DD). 없으면 '이 날짜만/이후 모두'는 비활성. */
  instanceDate?: string;
  defaultScope: RoutineScope;
  pending?: boolean;
  onConfirm: (scope: RoutineScope) => void;
}

function shortDate(iso?: string): string {
  if (!iso) return "";
  const [, m, d] = iso.split("-");
  return ` (${Number(m)}/${Number(d)})`;
}

/** 반복 루틴 수정/삭제 시 적용 범위(이 날짜만 / 이후 모두 / 전체)를 고른다. */
export function RoutineScopeDialog({
  open,
  onOpenChange,
  mode,
  instanceDate,
  defaultScope,
  pending = false,
  onConfirm,
}: Props) {
  const [scope, setScope] = useState<RoutineScope>(defaultScope);

  useEffect(() => {
    if (open) setScope(defaultScope);
  }, [open, defaultScope]);

  const options: { value: RoutineScope; label: string; needsDate: boolean }[] =
    [
      {
        value: "instance",
        label: `이 날짜만${shortDate(instanceDate)}`,
        needsDate: true,
      },
      { value: "following", label: "이 날짜 이후 모두", needsDate: true },
      { value: "all", label: "전체 루틴", needsDate: false },
    ];

  return (
    <Modal open={open} onOpenChange={onOpenChange} className="max-w-sm">
      <Dialog.Title className="text-base font-semibold">
        {mode === "edit" ? "이 루틴을 수정" : "이 루틴을 삭제"}
      </Dialog.Title>

      <fieldset className="mt-4 flex flex-col gap-2">
        {options.map((opt) => {
          const disabled = opt.needsDate && !instanceDate;
          return (
            <label
              key={opt.value}
              className="flex items-center gap-2 text-sm aria-disabled:opacity-50"
              aria-disabled={disabled}
            >
              <input
                type="radio"
                name="routine-scope"
                value={opt.value}
                checked={scope === opt.value}
                disabled={disabled}
                onChange={() => setScope(opt.value)}
              />
              {opt.label}
            </label>
          );
        })}
      </fieldset>

      <Modal.Footer>
        <Dialog.Close
          render={
            <Button variant="ghost" type="button" disabled={pending}>
              취소
            </Button>
          }
        />
        <Button
          type="button"
          variant={mode === "delete" ? "destructive" : "default"}
          disabled={pending}
          onClick={() => onConfirm(scope)}
        >
          확인
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
