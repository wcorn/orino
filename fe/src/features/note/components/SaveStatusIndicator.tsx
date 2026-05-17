import { AlertCircle, Check, Loader2 } from "lucide-react";

import { Button } from "@/components/ui/button";

import type { SaveStatus } from "../hooks/useAutoSaveNote";

interface Props {
  status: SaveStatus;
  savedAt: Date | null;
  onRetry: () => void;
}

function formatTime(date: Date): string {
  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  return `${hh}:${mm}`;
}

export function SaveStatusIndicator({ status, savedAt, onRetry }: Props) {
  if (status === "saving") {
    return (
      <span className="text-muted-foreground flex items-center gap-1.5 text-xs">
        <Loader2 className="size-3 animate-spin" />
        저장 중...
      </span>
    );
  }
  if (status === "error") {
    return (
      <span className="text-destructive flex items-center gap-1.5 text-xs">
        <AlertCircle className="size-3" />
        저장 실패
        <Button
          variant="ghost"
          size="sm"
          className="h-5 px-2 text-xs"
          onClick={onRetry}
        >
          재시도
        </Button>
      </span>
    );
  }
  if (status === "saved" && savedAt) {
    return (
      <span className="text-muted-foreground flex items-center gap-1.5 text-xs">
        <Check className="size-3" />
        저장됨 · {formatTime(savedAt)}
      </span>
    );
  }
  return null;
}
