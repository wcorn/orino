import { Dialog } from "@base-ui/react/dialog";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";
import { Select, type SelectOption } from "@/components/ui/select";

import { ColorSwatchSelector } from "./ColorSwatchSelector";
import { DAY_LABELS, type EditableBlock, isReversed } from "./planGrid";

const DAY_OPTIONS: SelectOption<string>[] = DAY_LABELS.map((label, i) => ({
  value: String(i),
  label,
}));

interface PlanBlockEditorProps {
  block: EditableBlock | null;
  onSave: (block: EditableBlock) => void;
  onDelete: (key: string) => void;
  onClose: () => void;
}

/** 블록 편집 모달: 요일·라벨·색·시작/종료 수정 + 삭제. 시간 역전이면 저장 차단(인라인 안내). */
export function PlanBlockEditor({
  block,
  onSave,
  onDelete,
  onClose,
}: PlanBlockEditorProps) {
  const [draft, setDraft] = useState<EditableBlock | null>(block);

  useEffect(() => {
    setDraft(block);
  }, [block]);

  if (!draft) return null;

  const reversed = isReversed(draft.startTime, draft.endTime);
  const labelEmpty = draft.label.trim().length === 0;

  const handleSave = () => {
    if (reversed || labelEmpty) return;
    onSave(draft);
  };

  return (
    <Modal
      open={block !== null}
      onOpenChange={(open) => !open && onClose()}
      className="max-w-sm"
    >
      <Dialog.Title className="text-base font-semibold">블록 편집</Dialog.Title>

      <div className="mt-4 flex flex-col gap-3">
        <FormField label="요일" labelId="block-day">
          <Select
            ariaLabelledby="block-day"
            value={String(draft.dayOfWeek)}
            onValueChange={(v) => setDraft({ ...draft, dayOfWeek: Number(v) })}
            options={DAY_OPTIONS}
          />
        </FormField>

        <FormField
          label="라벨"
          htmlFor="block-label"
          error={labelEmpty ? "라벨을 입력해 주세요." : undefined}
        >
          <Input
            id="block-label"
            value={draft.label}
            onChange={(e) => setDraft({ ...draft, label: e.target.value })}
            placeholder="예: 개인 프로젝트"
          />
        </FormField>

        <div className="flex gap-3">
          <FormField label="시작" htmlFor="block-start" className="flex-1">
            <Input
              id="block-start"
              type="time"
              value={draft.startTime}
              onChange={(e) =>
                setDraft({ ...draft, startTime: e.target.value })
              }
            />
          </FormField>
          <FormField
            label="종료"
            htmlFor="block-end"
            className="flex-1"
            error={reversed ? "종료가 시작보다 늦어야 합니다." : undefined}
          >
            <Input
              id="block-end"
              type="time"
              value={draft.endTime === "24:00" ? "" : draft.endTime}
              disabled={draft.endTime === "24:00"}
              onChange={(e) => setDraft({ ...draft, endTime: e.target.value })}
            />
          </FormField>
        </div>

        <label className="flex items-center gap-1.5 text-xs">
          <Checkbox
            checked={draft.endTime === "24:00"}
            onChange={(e) =>
              setDraft({
                ...draft,
                endTime: e.target.checked ? "24:00" : "23:00",
              })
            }
          />
          종료를 자정(24:00)으로
        </label>

        <FormField label="색" labelId="block-color">
          <ColorSwatchSelector
            value={draft.color}
            onChange={(key) => setDraft({ ...draft, color: key })}
            labelledBy="block-color"
          />
        </FormField>
      </div>

      <div className="mt-5 flex items-center justify-between">
        <Button
          variant="ghost"
          size="sm"
          className="text-destructive"
          onClick={() => onDelete(draft.key)}
        >
          삭제
        </Button>
        <div className="flex gap-2">
          <Dialog.Close render={<Button variant="outline" size="sm" />}>
            취소
          </Dialog.Close>
          <Button
            size="sm"
            onClick={handleSave}
            disabled={reversed || labelEmpty}
          >
            적용
          </Button>
        </div>
      </div>
    </Modal>
  );
}
