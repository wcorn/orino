import { useState } from "react";

interface Props {
  /** 현재 허용값(한 줄에 하나로 편집). */
  initial: string[];
  /** 정규화 전 원본 줄 목록을 넘긴다(서버가 공백·중복을 정리한다). */
  onSave: (options: string[]) => void;
  onClose: () => void;
}

/**
 * 열 허용값 목록(enum) 편집기(R3 #914). 한 줄에 하나씩. 느슨한 제약이라 이 목록은 셀 편집의
 * 드롭다운 제안으로만 쓰이고 값을 강제하지 않는다. 비워 저장하면 해제(자유 입력).
 */
export function ColumnOptionsEditor({ initial, onSave, onClose }: Props) {
  const [text, setText] = useState(initial.join("\n"));

  const save = () => {
    onSave(text.split("\n").map((s) => s.trim()));
  };

  return (
    <>
      <div className="fixed inset-0 z-40" onClick={onClose} />
      <div
        role="dialog"
        aria-label="허용값 목록 편집"
        className="border-border bg-popover fixed top-1/2 left-1/2 z-50 flex w-64 -translate-x-1/2 -translate-y-1/2 flex-col gap-2 rounded-md border p-3 text-sm shadow-md"
      >
        <div className="text-muted-foreground text-xs">
          허용값을 한 줄에 하나씩. 비우면 해제됩니다.
        </div>
        <textarea
          aria-label="허용값"
          value={text}
          onChange={(e) => setText(e.target.value)}
          rows={5}
          className="border-border rounded border px-2 py-1"
        />
        <div className="flex justify-end gap-1">
          <button
            type="button"
            onClick={onClose}
            className="hover:bg-accent rounded px-2 py-1 text-xs"
          >
            닫기
          </button>
          <button
            type="button"
            onClick={save}
            className="bg-primary text-primary-foreground rounded px-2 py-1 text-xs"
          >
            저장
          </button>
        </div>
      </div>
    </>
  );
}
