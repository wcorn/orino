import { X } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { type ToastItem, useToastStore } from "@/shared/lib/toast";

const VARIANT_STYLES: Record<string, string> = {
  info: "bg-background border-border text-foreground",
  success: "bg-primary text-primary-foreground border-primary",
  error: "bg-destructive text-destructive-foreground border-destructive",
};

export function Toaster() {
  const toasts = useToastStore((state) => state.toasts);

  return (
    <div
      role="region"
      aria-label="알림"
      className="pointer-events-none fixed right-4 bottom-4 z-[60] flex flex-col gap-2"
    >
      {toasts.map((item) => (
        <ToastRow key={item.id} item={item} />
      ))}
    </div>
  );
}

function ToastRow({ item }: { item: ToastItem }) {
  const dismiss = useToastStore((state) => state.dismiss);
  const runAction = useToastStore((state) => state.runAction);
  const remaining = useRemainingSeconds(item);
  const entered = useEnterTransition();

  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        "pointer-events-auto flex w-72 max-w-[calc(100vw-2rem)] items-start justify-between gap-2 rounded-lg border px-3 py-2 text-sm shadow-md",
        // fade + 아래에서 10px 올라오는 진입. 애니메이션 유틸리티 패키지를 더하지 않으려고
        // 마운트 후 클래스를 뒤집는 방식으로 낸다.
        "transition-all duration-200",
        entered ? "translate-y-0 opacity-100" : "translate-y-[10px] opacity-0",
        VARIANT_STYLES[item.variant],
      )}
    >
      <span className="flex-1">{item.message}</span>
      {item.action && (
        <Button
          variant="ghost"
          size="sm"
          onClick={() => runAction(item.id)}
          className="h-6 shrink-0 px-2 font-semibold underline-offset-2 hover:underline"
        >
          {item.action.label}
          {/* 남은 시간을 같이 보여줘 "언제까지 되돌릴 수 있는지"를 알린다. */}
          {remaining !== null && (
            <span className="ml-1 tabular-nums opacity-70">{remaining}</span>
          )}
        </Button>
      )}
      <Button
        variant="ghost"
        size="icon-sm"
        onClick={() => dismiss(item.id)}
        aria-label="알림 닫기"
        className="size-6 shrink-0 opacity-70 hover:opacity-100"
      >
        <X className="size-3.5" />
      </Button>
    </div>
  );
}

/**
 * 액션이 있는 스낵바의 남은 초. 액션이 없으면 null(카운트다운을 그리지 않는다).
 * 만료 자체는 store의 타이머가 처리하고 여기서는 표시만 한다 — 두 곳에서 닫으면 어긋난다.
 */
function useRemainingSeconds(item: ToastItem): number | null {
  const hasAction = Boolean(item.action);
  const [remaining, setRemaining] = useState(() => secondsLeft(item.expiresAt));

  useEffect(() => {
    if (!hasAction) return;
    setRemaining(secondsLeft(item.expiresAt));
    const timer = setInterval(
      () => setRemaining(secondsLeft(item.expiresAt)),
      250,
    );
    return () => clearInterval(timer);
  }, [hasAction, item.expiresAt]);

  return hasAction ? remaining : null;
}

function secondsLeft(expiresAt: number): number {
  return Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000));
}

/** 마운트 직후 한 번 true로 뒤집어 진입 트랜지션을 트리거한다. */
function useEnterTransition(): boolean {
  const [entered, setEntered] = useState(false);
  useEffect(() => {
    const frame = requestAnimationFrame(() => setEntered(true));
    return () => cancelAnimationFrame(frame);
  }, []);
  return entered;
}
