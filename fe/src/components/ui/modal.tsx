import { Dialog } from "@base-ui/react/dialog";
import type { ReactNode } from "react";

import { DialogFooter } from "./dialog-footer";
import { DialogFormFooter } from "./dialog-form-footer";
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
function ModalRoot({ open, onOpenChange, className, children }: ModalProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className={BACKDROP_CLASS} />
        <DialogPopup className={className}>{children}</DialogPopup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

/**
 * 모달의 단일 진입점. 푸터는 서브컴포넌트로 노출한다:
 * - `<Modal.Footer>` — 취소/확인 등 자유 구성 푸터
 * - `<Modal.FormFooter submitLabel pending onDelete>` — 폼 제출 푸터(취소+제출+삭제)
 * DialogPopup/DialogFooter/DialogFormFooter는 내부 구현이며 직접 import 대신 위를 쓴다.
 */
const Modal = Object.assign(ModalRoot, {
  Footer: DialogFooter,
  FormFooter: DialogFormFooter,
});

export { Modal };
