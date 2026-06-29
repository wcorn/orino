import { useEffect, useId, useState } from "react";

import { FieldError } from "@/components/ui/field-error";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";
import { Select, type SelectOption } from "@/components/ui/select";

import type { Material, MaterialStatus } from "../api/materials";
import { useUpdateMaterial } from "../hooks/useUpdateMaterial";

interface Props {
  material: Material;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const STATUS_OPTIONS: SelectOption<MaterialStatus>[] = [
  { value: "ACTIVE", label: "진행 중" },
  { value: "COMPLETED", label: "완료" },
];

export function EditMaterialDialog({ material, open, onOpenChange }: Props) {
  const titleId = useId();
  const statusLabelId = useId();
  const [title, setTitle] = useState(material.title);
  const [status, setStatus] = useState<MaterialStatus>(material.status);
  const mutation = useUpdateMaterial(material.id);

  useEffect(() => {
    if (open) {
      setTitle(material.title);
      setStatus(material.status);
      mutation.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, material.title, material.status]);

  const trimmed = title.trim();
  const titleValid = trimmed.length >= 1 && trimmed.length <= 200;
  const dirty = trimmed !== material.title || status !== material.status;

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!titleValid || !dirty || mutation.isPending) return;
    mutation.mutate(
      {
        title: trimmed !== material.title ? trimmed : undefined,
        status: status !== material.status ? status : undefined,
      },
      { onSuccess: () => onOpenChange(false) },
    );
  };

  return (
    <Modal open={open} onOpenChange={onOpenChange} title="자료 편집" size="sm">
      <form onSubmit={handleSubmit} className="mt-4 flex flex-col gap-4">
        <FormField label="제목" htmlFor={titleId}>
          <Input
            id={titleId}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={200}
            autoFocus
          />
        </FormField>

        <FormField label="상태" labelId={statusLabelId}>
          <Select
            value={status}
            onValueChange={setStatus}
            options={STATUS_OPTIONS}
            ariaLabelledby={statusLabelId}
          />
        </FormField>

        {mutation.isError && (
          <FieldError>저장에 실패했어요. 잠시 후 다시 시도해주세요.</FieldError>
        )}

        <Modal.Footer
          submitLabel="저장"
          pendingLabel="저장 중..."
          pending={mutation.isPending}
          submitDisabled={!titleValid || !dirty || mutation.isPending}
        />
      </form>
    </Modal>
  );
}
