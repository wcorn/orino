import { Dialog } from "@base-ui/react/dialog";
import { useEffect, useId, useState } from "react";

import { DialogFormFooter } from "@/components/ui/dialog-form-footer";
import { FieldError } from "@/components/ui/field-error";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";
import { Select, type SelectOption } from "@/components/ui/select";

import type { MaterialType } from "../api/materials";
import { useCreateMaterial } from "../hooks/useCreateMaterial";
import { MATERIAL_TYPE_ICONS, MATERIAL_TYPE_LABELS } from "../utils";

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated?: (materialId: number) => void;
}

const TYPE_OPTIONS: SelectOption<MaterialType>[] = (
  ["BOOK", "LECTURE", "WORKBOOK", "MOOC"] as const
).map((value) => ({
  value,
  label: `${MATERIAL_TYPE_ICONS[value]} ${MATERIAL_TYPE_LABELS[value]}`,
}));

export function AddMaterialDialog({ open, onOpenChange, onCreated }: Props) {
  const titleId = useId();
  const typeLabelId = useId();
  const [title, setTitle] = useState("");
  const [type, setType] = useState<MaterialType>("BOOK");
  const mutation = useCreateMaterial();

  useEffect(() => {
    if (open) {
      setTitle("");
      setType("BOOK");
      mutation.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const trimmedTitle = title.trim();
  const titleValid = trimmedTitle.length >= 1 && trimmedTitle.length <= 200;

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!titleValid || mutation.isPending) return;
    mutation.mutate(
      { title: trimmedTitle, type },
      {
        onSuccess: (data) => {
          onOpenChange(false);
          onCreated?.(data.material.id);
        },
      },
    );
  };

  return (
    <Modal open={open} onOpenChange={onOpenChange} className="max-w-sm">
      <Dialog.Title className="text-base font-semibold">
        학습 자료 추가
      </Dialog.Title>

      <form onSubmit={handleSubmit} className="mt-4 flex flex-col gap-4">
        <FormField label="제목" htmlFor={titleId}>
          <Input
            id={titleId}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="예: 이펙티브 자바"
            maxLength={200}
            autoFocus
          />
        </FormField>

        <FormField label="유형" labelId={typeLabelId}>
          <Select
            value={type}
            onValueChange={setType}
            options={TYPE_OPTIONS}
            ariaLabelledby={typeLabelId}
          />
        </FormField>

        {mutation.isError && (
          <FieldError>
            자료를 추가하지 못했어요. 잠시 후 다시 시도해주세요.
          </FieldError>
        )}

        <DialogFormFooter
          submitLabel="추가"
          pendingLabel="추가 중..."
          pending={mutation.isPending}
          submitDisabled={!titleValid || mutation.isPending}
        />
      </form>
    </Modal>
  );
}
