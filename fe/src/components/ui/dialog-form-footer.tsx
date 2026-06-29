import { Dialog } from "@base-ui/react/dialog";

import { Button } from "@/components/ui/button";
import { DialogFooter } from "@/components/ui/dialog-footer";
import { cn } from "@/lib/utils";

interface DialogFormFooterProps {
  /** 제출 버튼 라벨. */
  submitLabel: string;
  /** 제출 진행 중 라벨(예: "저장 중..."). */
  pendingLabel: string;
  /** 진행 중 여부 — 라벨 전환 + 취소/삭제 비활성. */
  pending: boolean;
  /** 제출 비활성 조건(호출부에서 `!valid || pending` 등 전체 식을 전달). */
  submitDisabled?: boolean;
  /** 있으면 왼쪽에 삭제 버튼(빨강) + 양끝 정렬. */
  onDelete?: () => void;
  deleteLabel?: string;
  cancelLabel?: string;
}

/**
 * 폼 다이얼로그 하단 액션 — 취소(Dialog.Close) + 제출(type=submit), 선택적 삭제(왼쪽).
 * `<form>` 안에서 사용한다(제출은 form submit).
 */
export function DialogFormFooter({
  submitLabel,
  pendingLabel,
  pending,
  submitDisabled,
  onDelete,
  deleteLabel = "삭제",
  cancelLabel = "취소",
}: DialogFormFooterProps) {
  return (
    <DialogFooter className={cn("mt-1", onDelete && "justify-between")}>
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
        <Button type="submit" disabled={submitDisabled}>
          {pending ? pendingLabel : submitLabel}
        </Button>
      </div>
    </DialogFooter>
  );
}
