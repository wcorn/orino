import { Dialog } from "@base-ui/react/dialog";
import { Select } from "@base-ui/react/select";
import { Check, ChevronDown } from "lucide-react";
import { useEffect, useId, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import type { MaterialType } from "../api/materials";
import { useCreateMaterial } from "../hooks/useCreateMaterial";
import { MATERIAL_TYPE_ICONS, MATERIAL_TYPE_LABELS } from "../utils";

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated?: (materialId: number) => void;
}

const TYPE_OPTIONS: MaterialType[] = ["BOOK", "LECTURE", "WORKBOOK", "MOOC"];

export function AddMaterialDialog({ open, onOpenChange, onCreated }: Props) {
  const titleId = useId();
  const typeLabelId = useId();
  const [title, setTitle] = useState("");
  const [type, setType] = useState<MaterialType>("BOOK");
  const mutation = useCreateMaterial();

  useEffect(() => {
    if (open) {
      setTitle("");
      setType("BOOK");
      mutation.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const trimmedTitle = title.trim();
  const titleValid = trimmedTitle.length >= 1 && trimmedTitle.length <= 200;

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!titleValid || mutation.isPending) return;
    mutation.mutate(
      { title: trimmedTitle, type },
      {
        onSuccess: (data) => {
          onOpenChange(false);
          onCreated?.(data.material.id);
        },
      },
    );
  };

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm transition-opacity duration-150 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0" />
        <Dialog.Popup className="bg-background fixed top-1/2 left-1/2 z-50 w-full max-w-sm -translate-x-1/2 -translate-y-1/2 rounded-xl border p-6 shadow-lg transition-all duration-150 data-[ending-style]:scale-95 data-[ending-style]:opacity-0 data-[starting-style]:scale-95 data-[starting-style]:opacity-0">
          <Dialog.Title className="text-base font-semibold">
            학습 자료 추가
          </Dialog.Title>

          <form onSubmit={handleSubmit} className="mt-4 flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <label htmlFor={titleId} className="text-sm font-medium">
                제목
              </label>
              <Input
                id={titleId}
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="예: 이펙티브 자바"
                maxLength={200}
                autoFocus
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <span id={typeLabelId} className="text-sm font-medium">
                유형
              </span>
              <Select.Root
                value={type}
                onValueChange={(value) => setType(value as MaterialType)}
              >
                <Select.Trigger
                  aria-labelledby={typeLabelId}
                  className="border-input bg-background focus-visible:ring-ring/30 flex h-9 items-center justify-between gap-2 rounded-md border px-3 text-sm shadow-xs focus-visible:ring-2 focus-visible:outline-none"
                >
                  <Select.Value>
                    {(value) => {
                      const t = value as MaterialType;
                      return (
                        <span>
                          {MATERIAL_TYPE_ICONS[t]} {MATERIAL_TYPE_LABELS[t]}
                        </span>
                      );
                    }}
                  </Select.Value>
                  <Select.Icon>
                    <ChevronDown className="size-4 opacity-60" />
                  </Select.Icon>
                </Select.Trigger>
                <Select.Portal>
                  <Select.Positioner sideOffset={4} className="z-50">
                    <Select.Popup className="bg-popover text-popover-foreground min-w-(--anchor-width) overflow-hidden rounded-md border p-1 shadow-md">
                      {TYPE_OPTIONS.map((value) => (
                        <Select.Item
                          key={value}
                          value={value}
                          className="data-[highlighted]:bg-accent data-[highlighted]:text-accent-foreground flex cursor-pointer items-center justify-between gap-2 rounded-sm px-2 py-1.5 text-sm outline-none"
                        >
                          <Select.ItemText>
                            <span>
                              {MATERIAL_TYPE_ICONS[value]}{" "}
                              {MATERIAL_TYPE_LABELS[value]}
                            </span>
                          </Select.ItemText>
                          <Select.ItemIndicator>
                            <Check className="size-3.5" />
                          </Select.ItemIndicator>
                        </Select.Item>
                      ))}
                    </Select.Popup>
                  </Select.Positioner>
                </Select.Portal>
              </Select.Root>
            </div>

            {mutation.isError && (
              <p className="text-destructive text-sm">
                자료를 추가하지 못했어요. 잠시 후 다시 시도해주세요.
              </p>
            )}

            <div className="mt-1 flex justify-end gap-2">
              <Dialog.Close
                render={
                  <Button
                    variant="ghost"
                    type="button"
                    disabled={mutation.isPending}
                  >
                    취소
                  </Button>
                }
              />
              <Button
                type="submit"
                disabled={!titleValid || mutation.isPending}
              >
                {mutation.isPending ? "추가 중..." : "추가"}
              </Button>
            </div>
          </form>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
