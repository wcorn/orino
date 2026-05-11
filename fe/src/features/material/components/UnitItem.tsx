import { Check, Pencil, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

import type { UnitSummary } from "../api/materials";

interface UnitItemProps {
  unit: UnitSummary;
  onComplete: (unit: UnitSummary) => void;
  onEdit: (unit: UnitSummary) => void;
  onDelete: (unit: UnitSummary) => void;
  completing?: boolean;
}

export function UnitItem({
  unit,
  onComplete,
  onEdit,
  onDelete,
  completing = false,
}: UnitItemProps) {
  const isCompleted = unit.status === "COMPLETED";

  return (
    <li
      className={cn(
        "border-border flex items-center gap-3 rounded-lg border px-3 py-2 text-sm",
        isCompleted && "text-muted-foreground",
      )}
    >
      <span
        className={cn(
          "flex size-5 shrink-0 items-center justify-center rounded-full border",
          isCompleted
            ? "border-primary bg-primary text-primary-foreground"
            : "border-border",
        )}
        aria-hidden
      >
        {isCompleted && <Check className="size-3" />}
      </span>
      <span className="text-muted-foreground w-6 shrink-0 text-xs">
        {unit.sortOrder}.
      </span>
      <span className={cn("flex-1 truncate", isCompleted && "line-through")}>
        {unit.title}
      </span>
      {!isCompleted && (
        <Button
          size="sm"
          variant="outline"
          onClick={() => onComplete(unit)}
          disabled={completing}
        >
          {completing ? "처리 중..." : "완료"}
        </Button>
      )}
      <Button
        variant="ghost"
        size="icon-sm"
        aria-label="단위 수정"
        onClick={() => onEdit(unit)}
      >
        <Pencil className="size-3.5" />
      </Button>
      <Button
        variant="ghost"
        size="icon-sm"
        aria-label="단위 삭제"
        onClick={() => onDelete(unit)}
      >
        <Trash2 className="size-3.5" />
      </Button>
    </li>
  );
}
