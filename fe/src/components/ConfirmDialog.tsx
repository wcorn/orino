import { Dialog } from "@base-ui/react/dialog";
import { type ReactNode } from "react";

import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/modal";

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
    <Modal open={open} onOpenChange={onOpenChange} className="max-w-sm">
      <Dialog.Title className="text-base font-semibold">{title}</Dialog.Title>
      {description && (
        <Dialog.Description className="text-muted-foreground mt-2 text-sm">
          {description}
        </Dialog.Description>
      )}
      <Modal.Footer>
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
      </Modal.Footer>
    </Modal>
  );
}
