import { useEffect, useId, useState } from "react";

import { FormField } from "@/components/ui/form-field";
import { Modal } from "@/components/ui/modal";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

const MAX_LEN = 1000;

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  mode: "create" | "edit";
  initialFront?: string;
  initialBack?: string;
  pending?: boolean;
  onSubmit: (values: { front: string; back: string }) => void;
  onDelete?: () => void;
}

export function FlashcardFormDialog({
  open,
  onOpenChange,
  mode,
  initialFront = "",
  initialBack = "",
  pending = false,
  onSubmit,
  onDelete,
}: Props) {
  const frontId = useId();
  const backId = useId();
  const [front, setFront] = useState(initialFront);
  const [back, setBack] = useState(initialBack);

  useEffect(() => {
    if (open) {
      setFront(initialFront);
      setBack(initialBack);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const frontTrim = front.trim();
  const backTrim = back.trim();
  const valid =
    frontTrim.length >= 1 &&
    frontTrim.length <= MAX_LEN &&
    backTrim.length >= 1 &&
    backTrim.length <= MAX_LEN;
  const dirty =
    mode === "create" || front !== initialFront || back !== initialBack;

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!valid || !dirty || pending) return;
    onSubmit({ front: frontTrim, back: backTrim });
  };

  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      title={mode === "create" ? "카드 추가" : "카드 편집"}
    >
      <form onSubmit={handleSubmit} className="mt-4 flex flex-col gap-4">
        <CharField
          id={frontId}
          label="앞면 (질문)"
          rows={3}
          value={front}
          onChange={setFront}
          max={MAX_LEN}
          autoFocus
        />
        <CharField
          id={backId}
          label="뒷면 (답)"
          rows={4}
          value={back}
          onChange={setBack}
          max={MAX_LEN}
        />

        <Modal.Footer
          submitLabel="저장"
          pendingLabel="저장 중..."
          pending={pending}
          submitDisabled={!valid || !dirty || pending}
          onDelete={mode === "edit" ? onDelete : undefined}
        />
      </form>
    </Modal>
  );
}

interface CharFieldProps {
  id: string;
  label: string;
  rows: number;
  value: string;
  onChange: (next: string) => void;
  max: number;
  autoFocus?: boolean;
}

function CharField({
  id,
  label,
  rows,
  value,
  onChange,
  max,
  autoFocus,
}: CharFieldProps) {
  const over = value.length > max;
  return (
    <FormField label={label} htmlFor={id}>
      <Textarea
        id={id}
        rows={rows}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoFocus={autoFocus}
      />
      <span
        className={cn(
          "text-muted-foreground self-end text-xs",
          over && "text-destructive",
        )}
      >
        {value.length} / {max}
      </span>
    </FormField>
  );
}
