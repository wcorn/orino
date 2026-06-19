import { Dialog } from "@base-ui/react/dialog";
import { useEffect, useId, useState } from "react";

import { Button } from "@/components/ui/button";
import { DialogPopup } from "@/components/ui/dialog-popup";
import { Input } from "@/components/ui/input";
import { GoogleConnectButton } from "@/features/google/components/GoogleConnectButton";

import type { TaskCreateRequest } from "../../api/tasks";

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  googleConnected: boolean;
  /** 기본 마감일(YYYY-MM-DD) */
  defaultDue: string;
  pending?: boolean;
  onSubmit: (values: TaskCreateRequest) => void;
}

export function TaskFormDialog({
  open,
  onOpenChange,
  googleConnected,
  defaultDue,
  pending = false,
  onSubmit,
}: Props) {
  const titleId = useId();
  const dueId = useId();
  const [title, setTitle] = useState("");
  const [due, setDue] = useState(defaultDue);

  useEffect(() => {
    if (open) {
      setTitle("");
      setDue(defaultDue);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const valid = title.trim().length > 0;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!valid || pending) return;
    onSubmit({ title: title.trim(), due: due || null });
  };

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm transition-opacity duration-150 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0" />
        <DialogPopup className="max-w-sm">
          <Dialog.Title className="text-base font-semibold">
            할 일 추가
          </Dialog.Title>

          {!googleConnected ? (
            <div className="mt-4 flex flex-col gap-4">
              <p className="text-muted-foreground text-sm">
                Google 연결이 필요합니다.
              </p>
              <div className="flex justify-end gap-2">
                <Dialog.Close
                  render={
                    <Button variant="ghost" type="button">
                      닫기
                    </Button>
                  }
                />
                <GoogleConnectButton />
              </div>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="mt-4 flex flex-col gap-3">
              <div className="flex flex-col gap-1.5">
                <label htmlFor={titleId} className="text-sm font-medium">
                  제목
                </label>
                <Input
                  id={titleId}
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  autoFocus
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label htmlFor={dueId} className="text-sm font-medium">
                  마감일 (선택)
                </label>
                <Input
                  id={dueId}
                  type="date"
                  value={due}
                  onChange={(e) => setDue(e.target.value)}
                />
              </div>

              <div className="mt-1 flex justify-end gap-2">
                <Dialog.Close
                  render={
                    <Button variant="ghost" type="button" disabled={pending}>
                      취소
                    </Button>
                  }
                />
                <Button type="submit" disabled={!valid || pending}>
                  {pending ? "저장 중..." : "저장"}
                </Button>
              </div>
            </form>
          )}
        </DialogPopup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
