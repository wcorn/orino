import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";
import { Textarea } from "@/components/ui/textarea";

import { useCreateFlow } from "../hooks/useFlowMutations";

interface FlowCreateModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/** 새 흐름 생성 모달. 생성 후 상세로 이동한다. */
export function FlowCreateModal({ open, onOpenChange }: FlowCreateModalProps) {
  const navigate = useNavigate();
  const createMutation = useCreateFlow();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");

  useEffect(() => {
    if (open) {
      setTitle("");
      setDescription("");
    }
  }, [open]);

  const submit = () => {
    const trimmed = title.trim();
    if (!trimmed) return;
    createMutation.mutate(
      { title: trimmed, description: description.trim() || null },
      {
        onSuccess: (flow) => {
          onOpenChange(false);
          navigate(`/lifelog/flows/${flow.id}`);
        },
      },
    );
  };

  return (
    <Modal open={open} onOpenChange={onOpenChange} title="새 흐름">
      <div className="mt-4 flex flex-col gap-3">
        <Input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="흐름 제목 (예: 제주 여행 2박3일)"
          aria-label="흐름 제목"
        />
        <Textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="설명 (선택)"
          rows={2}
          aria-label="흐름 설명"
        />
      </div>
      <Modal.Footer
        onSubmit={submit}
        submitLabel="만들기"
        pending={createMutation.isPending}
        pendingLabel="만드는 중..."
        submitDisabled={!title.trim() || createMutation.isPending}
      />
    </Modal>
  );
}
