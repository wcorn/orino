import { Dialog } from "@base-ui/react/dialog";
import { X } from "lucide-react";
import { type FormEvent, useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toast } from "@/shared/lib/toast";

import type { UnitSummary } from "../api/materials";
import { useUpdateUnit } from "../hooks/useUnits";

interface EditUnitDialogProps {
  materialId: number;
  unit: UnitSummary | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function EditUnitDialog({
  materialId,
  unit,
  open,
  onOpenChange,
}: EditUnitDialogProps) {
  const [title, setTitle] = useState("");
  const [sortOrder, setSortOrder] = useState<number>(1);
  const { mutateAsync, isPending } = useUpdateUnit(materialId);

  useEffect(() => {
    if (unit) {
      setTitle(unit.title);
      setSortOrder(unit.sortOrder);
    }
  }, [unit]);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!unit || !title.trim() || isPending) return;
    await mutateAsync({
      unitId: unit.id,
      request: { title: title.trim(), sortOrder },
    });
    toast("단위가 수정되었어요.", "success");
    onOpenChange(false);
  };

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm transition-opacity duration-150 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0" />
        <Dialog.Popup className="bg-background fixed top-1/2 left-1/2 z-50 w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-xl border p-6 shadow-lg transition-all duration-150 data-[ending-style]:scale-95 data-[ending-style]:opacity-0 data-[starting-style]:scale-95 data-[starting-style]:opacity-0">
          <div className="flex items-start justify-between gap-4">
            <Dialog.Title className="text-base font-semibold">
              단위 수정
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
            aria-label="단위 수정 폼"
          >
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="edit-unit-title">제목</Label>
              <Input
                id="edit-unit-title"
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                maxLength={200}
                autoFocus
                required
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="edit-unit-order">순서</Label>
              <Input
                id="edit-unit-order"
                type="number"
                min={1}
                value={sortOrder}
                onChange={(event) =>
                  setSortOrder(Math.max(1, Number(event.target.value) || 1))
                }
              />
            </div>
            <div className="mt-2 flex justify-end gap-2">
              <Dialog.Close
                render={
                  <Button variant="ghost" type="button">
                    취소
                  </Button>
                }
              />
              <Button type="submit" disabled={!title.trim() || isPending}>
                {isPending ? "저장 중..." : "저장"}
              </Button>
            </div>
          </form>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
