import { useEffect, useState } from "react";

import { Checkbox } from "@/components/ui/checkbox";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";
import { cn } from "@/lib/utils";

import { ColorSwatchSelector } from "./ColorSwatchSelector";
import { DEFAULT_COLOR } from "./planColors";
import {
  createBlocks,
  DAY_LABELS,
  type EditableBlock,
  isReversed,
} from "./planGrid";
import { TimeRangeFields } from "./TimeRangeFields";

interface PlanBlockCreateProps {
  open: boolean;
  onCreate: (blocks: EditableBlock[]) => void;
  onClose: () => void;
}

/** 블록 추가 모달: 여러 요일을 동시에 선택해 같은 시간/라벨/색의 블록을 한 번에 만든다. */
export function PlanBlockCreate({
  open,
  onCreate,
  onClose,
}: PlanBlockCreateProps) {
  const [days, setDays] = useState<Set<number>>(new Set());
  const [label, setLabel] = useState("");
  const [startTime, setStartTime] = useState("09:00");
  const [endTime, setEndTime] = useState("10:00");
  const [color, setColor] = useState(DEFAULT_COLOR);

  useEffect(() => {
    if (open) {
      setDays(new Set());
      setLabel("");
      setStartTime("09:00");
      setEndTime("10:00");
      setColor(DEFAULT_COLOR);
    }
  }, [open]);

  const reversed = isReversed(startTime, endTime);
  const labelEmpty = label.trim().length === 0;
  const noDay = days.size === 0;
  const invalid = reversed || labelEmpty || noDay;

  const toggleDay = (day: number) =>
    setDays((prev) => {
      const next = new Set(prev);
      if (next.has(day)) next.delete(day);
      else next.add(day);
      return next;
    });

  const handleSubmit = () => {
    if (invalid) return;
    const sorted = [...days].sort((a, b) => a - b);
    onCreate(createBlocks(sorted, startTime, endTime, label, color));
  };

  return (
    <Modal
      open={open}
      onOpenChange={(o) => !o && onClose()}
      title="블록 추가"
      size="sm"
    >
      <div className="mt-4 flex flex-col gap-3">
        <FormField
          label="요일(복수 선택)"
          labelId="create-days"
          error={noDay ? "요일을 1개 이상 선택하세요." : undefined}
        >
          <div
            className="flex gap-1.5"
            role="group"
            aria-labelledby="create-days"
          >
            {DAY_LABELS.map((dayLabel, i) => (
              <label
                key={dayLabel}
                className={cn(
                  "flex flex-1 cursor-pointer items-center justify-center rounded-md border py-1.5 text-xs font-medium",
                  days.has(i)
                    ? "border-primary bg-primary/10 text-primary"
                    : "text-foreground/70 hover:bg-muted",
                )}
              >
                <Checkbox
                  className="sr-only"
                  checked={days.has(i)}
                  onChange={() => toggleDay(i)}
                />
                {dayLabel}
              </label>
            ))}
          </div>
        </FormField>

        <FormField
          label="라벨"
          htmlFor="create-label"
          error={labelEmpty ? "라벨을 입력해 주세요." : undefined}
        >
          <Input
            id="create-label"
            value={label}
            onChange={(e) => setLabel(e.target.value)}
            placeholder="예: 개인 프로젝트"
          />
        </FormField>

        <TimeRangeFields
          startTime={startTime}
          endTime={endTime}
          onStartChange={setStartTime}
          onEndChange={setEndTime}
          reversed={reversed}
          idPrefix="create"
        />

        <FormField label="색" labelId="create-color">
          <ColorSwatchSelector
            value={color}
            onChange={setColor}
            labelledBy="create-color"
          />
        </FormField>
      </div>

      <Modal.Footer
        submitLabel="추가"
        onSubmit={handleSubmit}
        submitDisabled={invalid}
      />
    </Modal>
  );
}
