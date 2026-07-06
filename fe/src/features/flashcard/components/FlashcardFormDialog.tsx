import { useEffect, useId, useState } from "react";

import { Checkbox } from "@/components/ui/checkbox";
import { FormField } from "@/components/ui/form-field";
import { Modal } from "@/components/ui/modal";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

import type {
  FlashcardMutationPayload,
  FlashcardType,
  OrderingItem,
} from "../api/flashcards";
import {
  ensureMinItems,
  isOrderingValid,
  itemsEqual,
  normalizeItems,
} from "../orderingItems";
import { OrderingItemsEditor } from "./OrderingItemsEditor";

const MAX_LEN = 1000;

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  mode: "create" | "edit";
  initialType?: FlashcardType;
  initialFront?: string;
  initialBack?: string | null;
  initialItems?: OrderingItem[] | null;
  pending?: boolean;
  onSubmit: (
    payload: FlashcardMutationPayload,
    options: { bidirectional: boolean },
  ) => void;
  onDelete?: () => void;
}

export function FlashcardFormDialog({
  open,
  onOpenChange,
  mode,
  initialType = "BASIC",
  initialFront = "",
  initialBack = "",
  initialItems = null,
  pending = false,
  onSubmit,
  onDelete,
}: Props) {
  const frontId = useId();
  const backId = useId();
  const [type, setType] = useState<FlashcardType>(initialType);
  const [front, setFront] = useState(initialFront);
  const [back, setBack] = useState(initialBack ?? "");
  const [items, setItems] = useState<OrderingItem[]>(() =>
    ensureMinItems(initialItems ?? []),
  );
  const [bidirectional, setBidirectional] = useState(false);

  useEffect(() => {
    if (open) {
      setType(initialType);
      setFront(initialFront);
      setBack(initialBack ?? "");
      setItems(ensureMinItems(initialItems ?? []));
      setBidirectional(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const frontTrim = front.trim();
  const backTrim = back.trim();
  const frontValid = frontTrim.length >= 1 && frontTrim.length <= MAX_LEN;

  const valid =
    type === "ORDERING"
      ? frontValid && isOrderingValid(items, MAX_LEN)
      : frontValid && backTrim.length >= 1 && backTrim.length <= MAX_LEN;

  const initialItemsFilled = ensureMinItems(initialItems ?? []);
  const dirty =
    mode === "create" ||
    type !== initialType ||
    front !== initialFront ||
    (type === "BASIC"
      ? back !== (initialBack ?? "")
      : !itemsEqual(items, initialItemsFilled));

  // 양방향은 BASIC + 추가 모드 + 앞·뒤 채움일 때만
  const showBidirectional = mode === "create" && type === "BASIC";
  const bidirectionalEnabled = showBidirectional && valid;

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!valid || !dirty || pending) return;
    if (type === "ORDERING") {
      onSubmit(
        { type: "ORDERING", front: frontTrim, items: normalizeItems(items) },
        { bidirectional: false },
      );
    } else {
      onSubmit(
        { type: "BASIC", front: frontTrim, back: backTrim },
        { bidirectional: bidirectionalEnabled && bidirectional },
      );
    }
  };

  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      title={mode === "create" ? "카드 추가" : "카드 편집"}
    >
      <form onSubmit={handleSubmit} className="mt-4 flex flex-col gap-4">
        <TypeToggle value={type} onChange={setType} />

        <CharField
          id={frontId}
          label={type === "ORDERING" ? "지시문" : "앞면 (질문)"}
          rows={type === "ORDERING" ? 2 : 3}
          value={front}
          onChange={setFront}
          max={MAX_LEN}
          autoFocus
        />

        {type === "ORDERING" ? (
          <OrderingItemsEditor
            items={items}
            onChange={setItems}
            maxLen={MAX_LEN}
          />
        ) : (
          <CharField
            id={backId}
            label="뒷면 (답)"
            rows={4}
            value={back}
            onChange={setBack}
            max={MAX_LEN}
          />
        )}

        {showBidirectional && (
          <label
            className={cn(
              "flex items-start gap-2 text-sm",
              !bidirectionalEnabled && "opacity-50",
            )}
          >
            <Checkbox
              checked={bidirectional}
              disabled={!bidirectionalEnabled}
              onChange={(e) => setBidirectional(e.target.checked)}
              className="mt-0.5"
            />
            <span>
              <span className="font-medium">양방향</span>
              <span className="text-muted-foreground">
                {" "}
                — 역방향 카드도 함께 만들기 (앞↔뒤)
              </span>
            </span>
          </label>
        )}

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

interface TypeToggleProps {
  value: FlashcardType;
  onChange: (next: FlashcardType) => void;
}

function TypeToggle({ value, onChange }: TypeToggleProps) {
  const options: Array<{ key: FlashcardType; label: string }> = [
    { key: "BASIC", label: "기본" },
    { key: "ORDERING", label: "순서" },
  ];
  return (
    <div
      role="radiogroup"
      aria-label="카드 종류"
      className="bg-muted flex gap-1 rounded-lg p-1"
    >
      {options.map((opt) => (
        <button
          key={opt.key}
          type="button"
          role="radio"
          aria-checked={value === opt.key}
          onClick={() => onChange(opt.key)}
          className={cn(
            "flex-1 rounded-md py-1.5 text-sm font-medium transition-colors",
            value === opt.key
              ? "bg-background text-foreground shadow-sm"
              : "text-muted-foreground hover:text-foreground",
          )}
        >
          {opt.label}
        </button>
      ))}
    </div>
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
