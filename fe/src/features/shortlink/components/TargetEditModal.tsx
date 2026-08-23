import { type FormEvent, useEffect, useState } from "react";

import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";

interface TargetEditModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 지금 목적지. 입력칸의 시작값이다 — 대개 일부만 고친다. */
  currentTargetUrl: string;
  onSubmit: (targetUrl: string, reason: string) => void;
  pending: boolean;
}

/**
 * 목적지 교체(SL-008 · UC-01). <b>주소는 그대로 두고 목적지만 갈아끼운다.</b>
 *
 * <p>사유를 함께 받는 이유는 나중에 이력을 읽는 사람이 자기 자신이기 때문이다 —
 * `서명 만료로 재발급`이 적혀 있지 않으면, 6개월 뒤에는 왜 갈았는지 알 수 없다.
 * 다만 <b>필수는 아니다</b>. 사유를 강제하면 급할 때 아무 글자나 넣게 된다.
 */
export function TargetEditModal({
  open,
  onOpenChange,
  currentTargetUrl,
  onSubmit,
  pending,
}: TargetEditModalProps) {
  const [targetUrl, setTargetUrl] = useState(currentTargetUrl);
  const [reason, setReason] = useState("");

  // 모달을 다시 열면 지금 목적지에서 시작한다.
  useEffect(() => {
    if (open) {
      setTargetUrl(currentTargetUrl);
      setReason("");
    }
  }, [open, currentTargetUrl]);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const next = targetUrl.trim();
    if (!next || pending) {
      return;
    }
    onSubmit(next, reason.trim());
  };

  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      title="목적지 수정"
      description="주소는 그대로 두고 목적지만 갈아끼웁니다. 이미 뿌린 링크가 전부 살아납니다."
    >
      <form onSubmit={submit} className="mt-4 flex flex-col gap-3.5">
        <FormField label="새 목적지 URL" htmlFor="newTargetUrl">
          <Input
            id="newTargetUrl"
            value={targetUrl}
            onChange={(event) => setTargetUrl(event.target.value)}
            placeholder="https://…"
            autoFocus
          />
        </FormField>
        <FormField label="교체 사유 (선택)" htmlFor="targetChangeReason">
          <Input
            id="targetChangeReason"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder="예: 서명 만료로 재발급"
          />
        </FormField>
        <Modal.Footer
          submitLabel="바꾸기"
          pending={pending}
          pendingLabel="바꾸는 중..."
          submitDisabled={pending || targetUrl.trim() === ""}
        />
      </form>
    </Modal>
  );
}
