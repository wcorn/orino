import { Dialog } from "@base-ui/react/dialog";
import { Select } from "@base-ui/react/select";
import { Check, ChevronDown } from "lucide-react";
import { useEffect, useId, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import type { Material, MaterialStatus } from "../api/materials";
import { useUpdateMaterial } from "../hooks/useUpdateMaterial";

interface Props {
  material: Material;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const STATUS_OPTIONS: { value: MaterialStatus; label: string }[] = [
  { value: "ACTIVE", label: "진행 중" },
  { value: "COMPLETED", label: "완료" },
];

export function EditMaterialDialog({ material, open, onOpenChange }: Props) {
  const titleId = useId();
  const statusLabelId = useId();
  const [title, setTitle] = useState(material.title);
  const [status, setStatus] = useState<MaterialStatus>(material.status);
  const mutation = useUpdateMaterial(material.id);

  useEffect(() => {
    if (open) {
      setTitle(material.title);
      setStatus(material.status);
      mutation.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, material.title, material.status]);

  const trimmed = title.trim();
  const titleValid = trimmed.length >= 1 && trimmed.length <= 200;
  const dirty = trimmed !== material.title || status !== material.status;

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!titleValid || !dirty || mutation.isPending) return;
    mutation.mutate(
      {
        title: trimmed !== material.title ? trimmed : undefined,
        status: status !== material.status ? status : undefined,
      },
      { onSuccess: () => onOpenChange(false) },
    );
  };

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm transition-opacity duration-150 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0" />
        <Dialog.Popup className="bg-background fixed top-1/2 left-1/2 z-50 w-full max-w-sm -translate-x-1/2 -translate-y-1/2 rounded-xl border p-6 shadow-lg transition-all duration-150 data-[ending-style]:scale-95 data-[ending-style]:opacity-0 data-[starting-style]:scale-95 data-[starting-style]:opacity-0">
          <Dialog.Title className="text-base font-semibold">
            자료 편집
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
                maxLength={200}
                autoFocus
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <span id={statusLabelId} className="text-sm font-medium">
                상태
              </span>
              <Select.Root
                value={status}
                onValueChange={(value) => setStatus(value as MaterialStatus)}
              >
                <Select.Trigger
                  aria-labelledby={statusLabelId}
                  className="border-input bg-background focus-visible:ring-ring/30 flex h-9 items-center justify-between gap-2 rounded-md border px-3 text-sm shadow-xs focus-visible:ring-2 focus-visible:outline-none"
                >
                  <Select.Value>
                    {(value) =>
                      STATUS_OPTIONS.find((opt) => opt.value === value)?.label
                    }
                  </Select.Value>
                  <Select.Icon>
                    <ChevronDown className="size-4 opacity-60" />
                  </Select.Icon>
                </Select.Trigger>
                <Select.Portal>
                  <Select.Positioner sideOffset={4} className="z-50">
                    <Select.Popup className="bg-popover text-popover-foreground min-w-(--anchor-width) overflow-hidden rounded-md border p-1 shadow-md">
                      {STATUS_OPTIONS.map((opt) => (
                        <Select.Item
                          key={opt.value}
                          value={opt.value}
                          className="data-[highlighted]:bg-accent data-[highlighted]:text-accent-foreground flex cursor-pointer items-center justify-between gap-2 rounded-sm px-2 py-1.5 text-sm outline-none"
                        >
                          <Select.ItemText>{opt.label}</Select.ItemText>
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
                저장에 실패했어요. 잠시 후 다시 시도해주세요.
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
                disabled={!titleValid || !dirty || mutation.isPending}
              >
                {mutation.isPending ? "저장 중..." : "저장"}
              </Button>
            </div>
          </form>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
