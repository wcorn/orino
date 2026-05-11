import { Dialog } from "@base-ui/react/dialog";
import { X } from "lucide-react";
import { type FormEvent, useState } from "react";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { toast } from "@/shared/lib/toast";

import { useCreateUnits } from "../hooks/useUnits";

interface AddUnitsDialogProps {
  materialId: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

function parseLines(text: string): string[] {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}

export function AddUnitsDialog({
  materialId,
  open,
  onOpenChange,
}: AddUnitsDialogProps) {
  const [text, setText] = useState("");
  const { mutateAsync, isPending } = useCreateUnits(materialId);

  const titles = parseLines(text);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (titles.length === 0 || isPending) return;
    await mutateAsync(titles.map((title) => ({ title })));
    toast(`${titles.length}개의 단위가 추가되었어요.`, "success");
    setText("");
    onOpenChange(false);
  };

  return (
    <Dialog.Root
      open={open}
      onOpenChange={(isOpen) => {
        if (!isOpen) setText("");
        onOpenChange(isOpen);
      }}
    >
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm transition-opacity duration-150 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0" />
        <Dialog.Popup className="bg-background fixed top-1/2 left-1/2 z-50 w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-xl border p-6 shadow-lg transition-all duration-150 data-[ending-style]:scale-95 data-[ending-style]:opacity-0 data-[starting-style]:scale-95 data-[starting-style]:opacity-0">
          <div className="flex items-start justify-between gap-4">
            <Dialog.Title className="text-base font-semibold">
              학습 단위 추가
            </Dialog.Title>
            <Dialog.Close
              render={
                <Button variant="ghost" size="icon-sm" aria-label="닫기">
                  <X className="size-4" />
                </Button>
              }
            />
          </div>
          <form
            onSubmit={handleSubmit}
            className="mt-4 flex flex-col gap-4"
            aria-label="학습 단위 추가 폼"
          >
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="unit-titles">한 줄에 하나씩 입력</Label>
              <textarea
                id="unit-titles"
                value={text}
                onChange={(event) => setText(event.target.value)}
                placeholder={"예:\n아이템 1\n아이템 2\n아이템 3"}
                rows={8}
                className="border-border bg-background placeholder:text-muted-foreground focus-visible:ring-ring/50 min-h-32 w-full resize-y rounded-md border px-3 py-2 text-sm focus-visible:ring-3 focus-visible:outline-none"
                autoFocus
                required
              />
              <p className="text-muted-foreground text-xs">
                {titles.length}개 단위가 추가됩니다.
              </p>
            </div>
            <div className="mt-2 flex justify-end gap-2">
              <Dialog.Close
                render={
                  <Button variant="ghost" type="button">
                    취소
                  </Button>
                }
              />
              <Button type="submit" disabled={titles.length === 0 || isPending}>
                {isPending ? "추가 중..." : "추가"}
              </Button>
            </div>
          </form>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
