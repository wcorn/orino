import { type ReactNode } from "react";

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

/** Modal 위에 얹은 확인/삭제 프리셋. */
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
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      title={title}
      description={description}
      size="sm"
    >
      <Modal.Footer
        cancelLabel={cancelLabel}
        submitLabel={confirmLabel}
        onSubmit={onConfirm}
        destructive={destructive}
        pending={pending}
        pendingLabel="처리 중..."
      />
    </Modal>
  );
}
