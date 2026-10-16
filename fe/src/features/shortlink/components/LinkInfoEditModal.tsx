import { type FormEvent, useEffect, useState } from "react";

import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";
import { Textarea } from "@/components/ui/textarea";

interface LinkInfoEditModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  currentTitle: string | null;
  currentMemo: string | null;
  /** 빈 문자열은 「지운다」는 뜻이다 — 서버가 null로 저장한다. */
  onSubmit: (title: string, memo: string) => void;
  pending: boolean;
}

/**
 * 제목·설명 수정(#1424).
 *
 * <p>빠른 발급 바는 URL 하나만 받는다(명세 §4.1). 그렇게 만든 링크에 나중에 이름을 붙이는
 * 자리가 여기다 — 발급 때만 넣을 수 있으면 대부분의 링크가 끝내 이름 없이 남는다.
 *
 * <p>목적지 수정과 모달을 나눈다. 목적지 교체는 이미 뿌린 주소의 행선지를 바꾸는 일이라
 * 사유와 이력이 따르고, 이름 고치기는 내 목록의 표기를 바꾸는 일일 뿐이다.
 */
export function LinkInfoEditModal({
  open,
  onOpenChange,
  currentTitle,
  currentMemo,
  onSubmit,
  pending,
}: LinkInfoEditModalProps) {
  const [title, setTitle] = useState(currentTitle ?? "");
  const [memo, setMemo] = useState(currentMemo ?? "");

  // 모달을 다시 열면 지금 값에서 시작한다.
  useEffect(() => {
    if (open) {
      setTitle(currentTitle ?? "");
      setMemo(currentMemo ?? "");
    }
  }, [open, currentTitle, currentMemo]);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (pending) {
      return;
    }
    onSubmit(title.trim(), memo.trim());
  };

  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      title="제목·설명 수정"
      description="목록에서 알아보기 위한 이름이에요. 주소와 목적지는 그대로입니다."
    >
      <form onSubmit={submit} className="mt-4 flex flex-col gap-3.5">
        <FormField label="제목" htmlFor="editTitle">
          <Input
            id="editTitle"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            placeholder="비우면 짧은 주소"
            maxLength={100}
            autoFocus
          />
        </FormField>
        <FormField label="설명" htmlFor="editMemo">
          <Textarea
            id="editMemo"
            rows={3}
            value={memo}
            onChange={(event) => setMemo(event.target.value)}
            maxLength={255}
          />
        </FormField>
        <Modal.Footer
          submitLabel="저장"
          pending={pending}
          pendingLabel="저장하는 중..."
          submitDisabled={pending}
        />
      </form>
    </Modal>
  );
}
