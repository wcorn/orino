import { Dialog } from "@base-ui/react/dialog";
import { useEffect, useId, useState } from "react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const MAX_LEN = 1000;

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  mode: "create" | "edit";
  initialFront?: string;
  initialBack?: string;
  pending?: boolean;
  errorMessage?: string;
  onSubmit: (values: { front: string; back: string }) => void;
  onDelete?: () => void;
}

export function FlashcardFormDialog({
  open,
  onOpenChange,
  mode,
  initialFront = "",
  initialBack = "",
  pending = false,
  errorMessage,
  onSubmit,
  onDelete,
}: Props) {
  const frontId = useId();
  const backId = useId();
  const [front, setFront] = useState(initialFront);
  const [back, setBack] = useState(initialBack);

  useEffect(() => {
    if (open) {
      setFront(initialFront);
      setBack(initialBack);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const frontTrim = front.trim();
  const backTrim = back.trim();
  const valid =
    frontTrim.length >= 1 &&
    frontTrim.length <= MAX_LEN &&
    backTrim.length >= 1 &&
    backTrim.length <= MAX_LEN;
  const dirty =
    mode === "create" || front !== initialFront || back !== initialBack;

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!valid || !dirty || pending) return;
    onSubmit({ front: frontTrim, back: backTrim });
  };

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm transition-opacity duration-150 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0" />
        <Dialog.Popup className="bg-background fixed top-1/2 left-1/2 z-50 w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-xl border p-6 shadow-lg transition-all duration-150 data-[ending-style]:scale-95 data-[ending-style]:opacity-0 data-[starting-style]:scale-95 data-[starting-style]:opacity-0">
          <Dialog.Title className="text-base font-semibold">
            {mode === "create" ? "카드 추가" : "카드 편집"}
          </Dialog.Title>

          <form onSubmit={handleSubmit} className="mt-4 flex flex-col gap-4">
            <CharField
              id={frontId}
              label="앞면 (질문)"
              rows={3}
              value={front}
              onChange={setFront}
              max={MAX_LEN}
              autoFocus
            />
            <CharField
              id={backId}
              label="뒷면 (답)"
              rows={4}
              value={back}
              onChange={setBack}
              max={MAX_LEN}
            />

            {errorMessage && (
              <p className="text-destructive text-sm">{errorMessage}</p>
            )}

            <div className="mt-1 flex items-center justify-between gap-2">
              {mode === "edit" && onDelete ? (
                <Button
                  type="button"
                  variant="ghost"
                  className="text-destructive"
                  disabled={pending}
                  onClick={onDelete}
                >
                  삭제
                </Button>
              ) : (
                <span />
              )}
              <div className="flex gap-2">
                <Dialog.Close
                  render={
                    <Button variant="ghost" type="button" disabled={pending}>
                      취소
                    </Button>
                  }
                />
                <Button type="submit" disabled={!valid || !dirty || pending}>
                  {pending ? "저장 중..." : "저장"}
                </Button>
              </div>
            </div>
          </form>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

interface CharFieldProps {
  id: string;
  label: string;
  rows: number;
  value: string;
  onChange: (next: string) => void;
  max: number;
  autoFocus?: boolean;
}

function CharField({
  id,
  label,
  rows,
  value,
  onChange,
  max,
  autoFocus,
}: CharFieldProps) {
  const over = value.length > max;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <textarea
        id={id}
        rows={rows}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoFocus={autoFocus}
        className="border-input bg-background focus-visible:ring-ring/30 min-h-[3rem] resize-y rounded-md border p-2 text-sm shadow-xs focus-visible:ring-2 focus-visible:outline-none"
      />
      <span
        className={cn(
          "text-muted-foreground self-end text-xs",
          over && "text-destructive",
        )}
      >
        {value.length} / {max}
      </span>
    </div>
  );
}
