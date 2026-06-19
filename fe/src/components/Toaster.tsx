import { X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useToastStore } from "@/shared/lib/toast";

const VARIANT_STYLES: Record<string, string> = {
  info: "bg-background border-border text-foreground",
  success: "bg-primary text-primary-foreground border-primary",
  error: "bg-destructive text-destructive-foreground border-destructive",
};

export function Toaster() {
  const toasts = useToastStore((state) => state.toasts);
  const dismiss = useToastStore((state) => state.dismiss);

  return (
    <div
      role="region"
      aria-label="알림"
      className="pointer-events-none fixed right-4 bottom-4 z-[60] flex flex-col gap-2"
    >
      {toasts.map((toast) => (
        <div
          key={toast.id}
          role="status"
          aria-live="polite"
          className={cn(
            "pointer-events-auto flex w-72 max-w-[calc(100vw-2rem)] items-start justify-between gap-2 rounded-lg border px-3 py-2 text-sm shadow-md",
            VARIANT_STYLES[toast.variant],
          )}
        >
          <span className="flex-1">{toast.message}</span>
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={() => dismiss(toast.id)}
            aria-label="알림 닫기"
            className="size-6 shrink-0 opacity-70 hover:opacity-100"
          >
            <X className="size-3.5" />
          </Button>
        </div>
      ))}
    </div>
  );
}
