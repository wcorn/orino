import { Dialog } from "@base-ui/react/dialog";
import type { ReactNode } from "react";

import { DialogPopup } from "./dialog-popup";

const BACKDROP_CLASS =
  "fixed inset-0 z-50 bg-black/50 backdrop-blur-sm transition-opacity duration-150 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0";

interface ModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 폭 제어용 className (예: max-w-sm / max-w-md). */
  className?: string;
  children: ReactNode;
}

/**
 * 표준 모달 골격: Root + Portal + Backdrop + DialogPopup을 한 번에 캡슐화한다.
 * 내부에서 base-ui의 Dialog.Title / Dialog.Close 등을 children으로 그대로 사용한다.
 */
function Modal({ open, onOpenChange, className, children }: ModalProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className={BACKDROP_CLASS} />
        <DialogPopup className={className}>{children}</DialogPopup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

export { Modal };
