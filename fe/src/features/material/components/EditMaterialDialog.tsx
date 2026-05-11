import { Dialog } from "@base-ui/react/dialog";
import { Select } from "@base-ui/react/select";
import { Check, ChevronDown, X } from "lucide-react";
import { type FormEvent, useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toast } from "@/shared/lib/toast";

import type { MaterialDetail, MaterialStatus } from "../api/materials";
import { useUpdateMaterial } from "../hooks/useMaterials";

const STATUS_OPTIONS: { value: MaterialStatus; label: string }[] = [
  { value: "ACTIVE", label: "진행 중" },
  { value: "COMPLETED", label: "완료" },
];

interface EditMaterialDialogProps {
  material: MaterialDetail;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onRequestDelete: () => void;
}

export function EditMaterialDialog({
  material,
  open,
  onOpenChange,
  onRequestDelete,
}: EditMaterialDialogProps) {
  const [title, setTitle] = useState(material.title);
  const [status, setStatus] = useState<MaterialStatus>(material.status);
  const { mutateAsync, isPending } = useUpdateMaterial(material.id);

  useEffect(() => {
    if (open) {
      setTitle(material.title);
      setStatus(material.status);
    }
  }, [open, material.title, material.status]);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!title.trim() || isPending) return;
    await mutateAsync({ title: title.trim(), status });
    toast("자료가 수정되었어요.", "success");
    onOpenChange(false);
  };

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm transition-opacity duration-150 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0" />
        <Dialog.Popup className="bg-background fixed top-1/2 left-1/2 z-50 w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-xl border p-6 shadow-lg transition-all duration-150 data-[ending-style]:scale-95 data-[ending-style]:opacity-0 data-[starting-style]:scale-95 data-[starting-style]:opacity-0">
          <div className="flex items-start justify-between gap-4">
            <Dialog.Title className="text-base font-semibold">
              자료 편집
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
            aria-label="자료 편집 폼"
          >
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="edit-material-title">제목</Label>
              <Input
                id="edit-material-title"
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                maxLength={200}
                autoFocus
                required
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="edit-material-status">상태</Label>
              <Select.Root
                value={status}
                onValueChange={(value) => setStatus(value as MaterialStatus)}
              >
                <Select.Trigger
                  id="edit-material-status"
                  className="border-border bg-background hover:bg-muted/50 focus-visible:ring-ring/50 flex h-9 w-full items-center justify-between rounded-md border px-3 text-sm focus-visible:ring-3 focus-visible:outline-none"
                >
                  <Select.Value />
                  <Select.Icon>
                    <ChevronDown className="text-muted-foreground size-4" />
                  </Select.Icon>
                </Select.Trigger>
                <Select.Portal>
                  <Select.Positioner sideOffset={4} className="z-50">
                    <Select.Popup className="bg-popover text-popover-foreground min-w-[var(--anchor-width)] overflow-hidden rounded-md border p-1 shadow-md">
                      {STATUS_OPTIONS.map((option) => (
                        <Select.Item
                          key={option.value}
                          value={option.value}
                          className="hover:bg-muted data-[highlighted]:bg-muted relative flex h-8 cursor-pointer items-center justify-between rounded px-2 text-sm select-none"
                        >
                          <Select.ItemText>{option.label}</Select.ItemText>
                          <Select.ItemIndicator>
                            <Check className="text-primary size-4" />
                          </Select.ItemIndicator>
                        </Select.Item>
                      ))}
                    </Select.Popup>
                  </Select.Positioner>
                </Select.Portal>
              </Select.Root>
            </div>

            <div className="mt-2 flex items-center justify-between gap-2">
              <Button
                type="button"
                variant="destructive"
                onClick={() => {
                  onOpenChange(false);
                  onRequestDelete();
                }}
              >
                자료 삭제
              </Button>
              <div className="flex gap-2">
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
            </div>
          </form>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
