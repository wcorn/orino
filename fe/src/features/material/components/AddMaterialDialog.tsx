import { Dialog } from "@base-ui/react/dialog";
import { Select } from "@base-ui/react/select";
import { Check, ChevronDown, X } from "lucide-react";
import { type FormEvent, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

import type { MaterialType } from "../api/materials";
import { useCreateMaterial } from "../hooks/useMaterials";

const TYPE_OPTIONS: { value: MaterialType; label: string }[] = [
  { value: "BOOK", label: "책" },
  { value: "LECTURE", label: "강의" },
  { value: "WORKBOOK", label: "문제집" },
  { value: "MOOC", label: "MOOC" },
];

interface AddMaterialDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function AddMaterialDialog({
  open,
  onOpenChange,
}: AddMaterialDialogProps) {
  const [title, setTitle] = useState("");
  const [type, setType] = useState<MaterialType>("BOOK");
  const { mutateAsync, isPending } = useCreateMaterial();

  const reset = () => {
    setTitle("");
    setType("BOOK");
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!title.trim() || isPending) return;
    await mutateAsync({ title: title.trim(), type });
    reset();
    onOpenChange(false);
  };

  return (
    <Dialog.Root
      open={open}
      onOpenChange={(isOpen) => {
        if (!isOpen) reset();
        onOpenChange(isOpen);
      }}
    >
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm transition-opacity duration-150 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0" />
        <Dialog.Popup className="bg-background fixed top-1/2 left-1/2 z-50 w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-xl border p-6 shadow-lg transition-all duration-150 data-[ending-style]:scale-95 data-[ending-style]:opacity-0 data-[starting-style]:scale-95 data-[starting-style]:opacity-0">
          <div className="flex items-start justify-between gap-4">
            <Dialog.Title className="text-base font-semibold">
              학습 자료 추가
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
            aria-label="학습 자료 추가 폼"
          >
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="material-title">제목</Label>
              <Input
                id="material-title"
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                placeholder="예: 이펙티브 자바"
                maxLength={200}
                autoFocus
                required
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="material-type">유형</Label>
              <Select.Root
                value={type}
                onValueChange={(value) => setType(value as MaterialType)}
              >
                <Select.Trigger
                  id="material-type"
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
                      {TYPE_OPTIONS.map((option) => (
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

            <div className="mt-2 flex justify-end gap-2">
              <Dialog.Close
                render={
                  <Button variant="ghost" type="button">
                    취소
                  </Button>
                }
              />
              <Button type="submit" disabled={!title.trim() || isPending}>
                {isPending ? "추가 중..." : "추가"}
              </Button>
            </div>
          </form>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
