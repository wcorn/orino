import { Dialog } from "@base-ui/react/dialog";
import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

import { Button } from "./button";

const BACKDROP_CLASS =
  "fixed inset-0 z-50 bg-black/50 backdrop-blur-sm transition-opacity duration-150 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0";

// 중앙정렬 + 진입/종료 애니메이션 + max-height·세로 스크롤·좌우 안전 여백 (구 DialogPopup 인라인).
const POPUP_CLASS =
  "bg-background fixed top-1/2 left-1/2 z-50 max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-xl border p-6 shadow-lg transition-all duration-150 data-[ending-style]:scale-95 data-[ending-style]:opacity-0 data-[starting-style]:scale-95 data-[starting-style]:opacity-0";

const SIZE_CLASS: Record<"sm" | "md" | "lg", string> = {
  sm: "max-w-sm",
  md: "max-w-md",
  lg: "max-w-lg",
};

interface ModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 제목 — Dialog.Title로 렌더(접근성 필수). */
  title: ReactNode;
  /** 제목 아래 보조 설명(선택). */
  description?: ReactNode;
  /** 폭 프리셋(max-width). 기본 md. */
  size?: "sm" | "md" | "lg";
  /** 폭 미세조정용 추가 className(선택). */
  className?: string;
  /** 본문 + Modal.Footer. */
  children?: ReactNode;
}

/**
 * 유일한 범용 다이얼로그 진입점. Root+Portal+Backdrop+Popup을 캡슐화하고
 * 제목/설명/폭을 prop으로 받는다. 하단 액션은 `<Modal.Footer>` 서브컴포넌트로 구성한다.
 */
function ModalRoot({
  open,
  onOpenChange,
  title,
  description,
  size = "md",
  className,
  children,
}: ModalProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className={BACKDROP_CLASS} />
        <Dialog.Popup className={cn(POPUP_CLASS, SIZE_CLASS[size], className)}>
          <Dialog.Title className="text-heading font-semibold">
            {title}
          </Dialog.Title>
          {description && (
            <Dialog.Description className="text-muted-foreground text-label mt-1">
              {description}
            </Dialog.Description>
          )}
          {children}
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

const FOOTER_CLASS = "mt-5 flex items-center justify-end gap-2";

interface ModalFooterProps {
  /** 주면 프리셋(취소/제출) 대신 이 내용을 푸터 레이아웃에 렌더(커스텀 액션). */
  children?: ReactNode;
  cancelLabel?: string;
  submitLabel?: string;
  /** 주면 제출 버튼이 type=button onClick, 없으면 type=submit(폼 제출). */
  onSubmit?: () => void;
  /** 진행 중 — 라벨 전환 + 취소/삭제 비활성. */
  pending?: boolean;
  /** 진행 중 제출 라벨(예: "저장 중..."). */
  pendingLabel?: string;
  /** 제출 비활성 조건(없으면 pending). */
  submitDisabled?: boolean;
  /** 제출을 위험 액션(빨강)으로. */
  destructive?: boolean;
  /** 있으면 왼쪽에 삭제 버튼 + 양끝 정렬. */
  onDelete?: () => void;
  deleteLabel?: string;
  className?: string;
}

/**
 * 다이얼로그 하단 액션. 기본은 취소(Dialog.Close)+제출 프리셋, `onDelete`면 왼쪽 삭제.
 * `<form>` 안에서 `onSubmit` 없이 쓰면 제출이 form submit(type=submit)으로 동작한다.
 * 커스텀 구성이 필요하면 `children`을 넘긴다.
 */
function ModalFooter({
  children,
  cancelLabel = "취소",
  submitLabel = "확인",
  onSubmit,
  pending = false,
  pendingLabel,
  submitDisabled,
  destructive = false,
  onDelete,
  deleteLabel = "삭제",
  className,
}: ModalFooterProps) {
  if (children) {
    return <div className={cn(FOOTER_CLASS, className)}>{children}</div>;
  }
  return (
    <div className={cn(FOOTER_CLASS, onDelete && "justify-between", className)}>
      {onDelete && (
        <Button
          type="button"
          variant="ghost"
          className="text-destructive"
          disabled={pending}
          onClick={onDelete}
        >
          {deleteLabel}
        </Button>
      )}
      <div className="flex gap-2">
        <Dialog.Close
          render={
            <Button variant="ghost" type="button" disabled={pending}>
              {cancelLabel}
            </Button>
          }
        />
        <Button
          type={onSubmit ? "button" : "submit"}
          variant={destructive ? "destructive" : "default"}
          onClick={onSubmit}
          disabled={submitDisabled ?? pending}
        >
          {pending && pendingLabel ? pendingLabel : submitLabel}
        </Button>
      </div>
    </div>
  );
}

const Modal = Object.assign(ModalRoot, { Footer: ModalFooter });

export { Modal };
