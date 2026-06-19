import { Dialog } from "@base-ui/react/dialog";
import { type ReactNode } from "react";

import { Button } from "@/components/ui/button";
import { DialogPopup } from "@/components/ui/dialog-popup";

interface ConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  destructive?: boolean;
  onConfirm: () => void;
  pending?: boolean;
}

export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel = "확인",
  cancelLabel = "취소",
  destructive = false,
  onConfirm,
  pending = false,
}: ConfirmDialogProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm transition-opacity duration-150 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0" />
        <DialogPopup className="max-w-sm">
          <Dialog.Title className="text-base font-semibold">
            {title}
          </Dialog.Title>
          {description && (
            <Dialog.Description className="text-muted-foreground mt-2 text-sm">
              {description}
            </Dialog.Description>
          )}
          <div className="mt-5 flex justify-end gap-2">
            <Dialog.Close
              render={
                <Button variant="ghost" type="button" disabled={pending}>
                  {cancelLabel}
                </Button>
              }
            />
            <Button
              type="button"
              variant={destructive ? "destructive" : "default"}
              onClick={onConfirm}
              disabled={pending}
            >
              {pending ? "처리 중..." : confirmLabel}
            </Button>
          </div>
        </DialogPopup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
