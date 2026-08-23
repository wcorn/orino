import { Lock } from "lucide-react";
import { type FormEvent, useState } from "react";

import { Input } from "@/components/ui/input";
import { Modal } from "@/components/ui/modal";
import { Switch } from "@/components/ui/switch";

interface PasswordCardProps {
  hasPassword: boolean;
  onSet: (password: string) => void;
  onClear: () => void;
  pending: boolean;
}

/**
 * 상세 화면의 비밀번호 보호(명세 §10).
 *
 * <p><b>거는 것과 푸는 것이 대칭이 아니다.</b> 걸 때는 값을 받아야 하므로 작은 모달을 한 번
 * 거치고, 풀 때는 스위치를 끄는 즉시 {@code password: null}로 해제한다 — 잘못 퍼진 링크를
 * 급히 열어 줘야 하는 상황에서 확인 창을 한 번 더 띄우는 것은 방해다.
 *
 * <p>지금 걸린 비밀번호가 무엇인지는 <b>보여 줄 수 없다</b>(BCrypt로만 저장한다).
 * 잊었으면 새로 걸면 된다.
 */
export function PasswordCard({
  hasPassword,
  onSet,
  onClear,
  pending,
}: PasswordCardProps) {
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState("");

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!password.trim() || pending) {
      return;
    }
    onSet(password.trim());
    setPassword("");
    setOpen(false);
  };

  return (
    <>
      <div className="flex items-center justify-between gap-3">
        <span className="text-muted-foreground flex items-center gap-2 text-[13px]">
          <Lock className="size-3.5" />
          비밀번호 보호
          {hasPassword && (
            <span className="text-xs">
              — 방문자가 확인 화면을 한 번 거칩니다
            </span>
          )}
        </span>
        <Switch
          aria-label="비밀번호 보호"
          checked={hasPassword}
          disabled={pending}
          onCheckedChange={(next) => {
            if (next) {
              setOpen(true);
            } else {
              onClear();
            }
          }}
        />
      </div>

      <Modal
        open={open}
        onOpenChange={(next) => {
          setOpen(next);
          if (!next) {
            setPassword("");
          }
        }}
        title="비밀번호 걸기"
        description="방문자는 이 비밀번호를 넣어야 목적지로 갑니다. 통과해도 다음 방문에 다시 물어봅니다."
        size="sm"
      >
        <form onSubmit={submit} className="mt-4 flex flex-col gap-3">
          <Input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            aria-label="비밀번호"
            placeholder="방문자가 입력할 비밀번호"
            autoComplete="new-password"
            autoFocus
          />
          <Modal.Footer
            submitLabel="걸기"
            pending={pending}
            pendingLabel="거는 중..."
            submitDisabled={pending || password.trim() === ""}
          />
        </form>
      </Modal>
    </>
  );
}
