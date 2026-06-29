import { Dialog } from "@base-ui/react/dialog";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";
import { Select, type SelectOption } from "@/components/ui/select";

import { ColorSwatchSelector } from "./ColorSwatchSelector";
import { DAY_LABELS, type EditableBlock, isReversed } from "./planGrid";
import { TimeRangeFields } from "./TimeRangeFields";

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

        <TimeRangeFields
          startTime={draft.startTime}
          endTime={draft.endTime}
          onStartChange={(v) => setDraft({ ...draft, startTime: v })}
          onEndChange={(v) => setDraft({ ...draft, endTime: v })}
          reversed={reversed}
          idPrefix="block"
        />

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
