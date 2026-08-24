import { Link2 } from "lucide-react";
import { type FormEvent, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

interface QuickCreateBarProps {
  onCreate: (targetUrl: string) => void;
  pending: boolean;
  /** 좁은 화면. 안내 문구를 줄이고 버튼을 눈에 띄게 바꾼다(화면 설계 §6). */
  compact?: boolean;
}

/**
 * 빠른 발급 바(SL-002). <b>목록 맨 위에 상주한다</b> — 데스크탑에서도 모바일에서도.
 *
 * <p>링크를 만드는 순간은 대개 폰에서 뭔가를 보내려던 순간이고, 그때 모달을 한 번 여는 것과
 * 붙여넣고 Enter를 누르는 것의 차이가 이 기능을 쓰느냐 마느냐를 가른다(명세 §4.1 · §5.4).
 * 그래서 여기에는 <b>입력칸 하나와 버튼 하나뿐</b>이다. 메모 · 태그 · 만료 · 비밀번호는
 * 모달에 있고, 모달은 옵션이지 기본이 아니다.
 */
export function QuickCreateBar({
  onCreate,
  pending,
  compact = false,
}: QuickCreateBarProps) {
  const [value, setValue] = useState("");

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const targetUrl = value.trim();
    if (!targetUrl || pending) {
      return;
    }
    onCreate(targetUrl);
    // 입력칸은 바로 비운다. 연달아 붙여넣는 흐름을 끊지 않는다.
    setValue("");
  };

  return (
    <form
      onSubmit={submit}
      className="bg-card ring-foreground/10 flex items-center gap-2 rounded-xl p-3 ring-1"
    >
      <span className="bg-accent text-accent-foreground grid size-8 shrink-0 place-items-center rounded-lg">
        <Link2 className="size-4" />
      </span>
      <Input
        value={value}
        onChange={(event) => setValue(event.target.value)}
        aria-label="빠른 발급 URL"
        placeholder={
          compact
            ? "URL 붙여넣기"
            : "URL 붙여넣고 Enter — 짧은 주소가 바로 만들어지고 클립보드에 복사돼요"
        }
        className="border-none bg-transparent shadow-none focus-visible:ring-0"
      />
      <Button
        type="submit"
        // 모바일에서는 이 버튼이 발급의 주 경로다 — 눈에 띄는 쪽으로 둔다(화면 설계 §6).
        variant={compact ? "default" : "secondary"}
        size="sm"
        disabled={pending || value.trim() === ""}
      >
        {pending ? "만드는 중..." : "만들기"}
      </Button>
    </form>
  );
}
