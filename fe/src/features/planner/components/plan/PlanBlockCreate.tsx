import { Dialog } from "@base-ui/react/dialog";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";
import { cn } from "@/lib/utils";

import { DEFAULT_COLOR, PLAN_COLORS } from "./planColors";
import {
  createBlocks,
  DAY_LABELS,
  type EditableBlock,
  isReversed,
} from "./planGrid";

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
      className="max-w-sm"
    >
      <Dialog.Title className="text-base font-semibold">블록 추가</Dialog.Title>

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

        <div className="flex gap-3">
          <FormField label="시작" htmlFor="create-start" className="flex-1">
            <Input
              id="create-start"
              type="time"
              value={startTime}
              onChange={(e) => setStartTime(e.target.value)}
            />
          </FormField>
          <FormField
            label="종료"
            htmlFor="create-end"
            className="flex-1"
            error={reversed ? "종료가 시작보다 늦어야 합니다." : undefined}
          >
            <Input
              id="create-end"
              type="time"
              value={endTime}
              onChange={(e) => setEndTime(e.target.value)}
            />
          </FormField>
        </div>

        <FormField label="색" labelId="create-color">
          <div
            className="flex gap-2"
            role="group"
            aria-labelledby="create-color"
          >
            {PLAN_COLORS.map((c) => (
              <button
                key={c.key}
                type="button"
                aria-label={c.label}
                aria-pressed={color === c.key}
                onClick={() => setColor(c.key)}
                className={cn(
                  "size-6 rounded-full ring-offset-2 transition",
                  c.swatch,
                  color === c.key
                    ? "ring-ring ring-2"
                    : "opacity-70 hover:opacity-100",
                )}
              />
            ))}
          </div>
        </FormField>
      </div>

      <div className="mt-5 flex justify-end gap-2">
        <Dialog.Close render={<Button variant="outline" size="sm" />}>
          취소
        </Dialog.Close>
        <Button size="sm" onClick={handleSubmit} disabled={invalid}>
          추가
        </Button>
      </div>
    </Modal>
  );
}
