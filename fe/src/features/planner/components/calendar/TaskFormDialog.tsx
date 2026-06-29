import { Dialog } from "@base-ui/react/dialog";
import { useEffect, useId, useState } from "react";

import { DialogFormFooter } from "@/components/ui/dialog-form-footer";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";
import { GoogleRequiredState } from "@/features/google/components/GoogleRequiredState";

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
    <Modal open={open} onOpenChange={onOpenChange} className="max-w-sm">
      <Dialog.Title className="text-base font-semibold">
        할 일 추가
      </Dialog.Title>

      {!googleConnected ? (
        <GoogleRequiredState />
      ) : (
        <form onSubmit={handleSubmit} className="mt-4 flex flex-col gap-3">
          <FormField label="제목" htmlFor={titleId}>
            <Input
              id={titleId}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              autoFocus
            />
          </FormField>
          <FormField label="마감일 (선택)" htmlFor={dueId}>
            <Input
              id={dueId}
              type="date"
              value={due}
              onChange={(e) => setDue(e.target.value)}
            />
          </FormField>

          <DialogFormFooter
            submitLabel="저장"
            pendingLabel="저장 중..."
            pending={pending}
            submitDisabled={!valid || pending}
          />
        </form>
      )}
    </Modal>
  );
}
