import { useEffect, useState } from "react";

import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";
import { Textarea } from "@/components/ui/textarea";

import type { FlowDetail, FlowStatus } from "../api/flows";
import { useUpdateFlow } from "../hooks/useFlowMutations";
import { keyFromUrl } from "../lib/photoKey";

interface FlowEditModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  flow: FlowDetail;
}

/** 흐름 제목·설명·상태 수정. 커버는 현재 보이는 이미지를 유지한다. */
export function FlowEditModal({
  open,
  onOpenChange,
  flow,
}: FlowEditModalProps) {
  const updateMutation = useUpdateFlow();
  const [title, setTitle] = useState(flow.title);
  const [description, setDescription] = useState(flow.description ?? "");
  const [status, setStatus] = useState<FlowStatus>(flow.status);

  useEffect(() => {
    if (open) {
      setTitle(flow.title);
      setDescription(flow.description ?? "");
      setStatus(flow.status);
    }
  }, [open, flow]);

  const submit = () => {
    const trimmed = title.trim();
    if (!trimmed) return;
    updateMutation.mutate(
      {
        id: flow.id,
        request: {
          title: trimmed,
          description: description.trim() || null,
          // 응답엔 objectKey가 없어 현재 커버 URL에서 유도해 보이는 이미지를 유지한다.
          coverObjectKey: keyFromUrl(flow.coverUrl),
          status,
        },
      },
      { onSuccess: () => onOpenChange(false) },
    );
  };

  return (
    <Modal open={open} onOpenChange={onOpenChange} title="흐름 수정">
      <div className="mt-4 flex flex-col gap-3">
        <Input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          aria-label="흐름 제목"
        />
        <Textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="설명 (선택)"
          rows={2}
          aria-label="흐름 설명"
        />
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={status === "ARCHIVED"}
            onChange={(e) =>
              setStatus(e.target.checked ? "ARCHIVED" : "ACTIVE")
            }
          />
          보관됨
        </label>
      </div>
      <Modal.Footer
        onSubmit={submit}
        submitLabel="저장"
        pending={updateMutation.isPending}
        pendingLabel="저장 중..."
        submitDisabled={!title.trim() || updateMutation.isPending}
      />
    </Modal>
  );
}
