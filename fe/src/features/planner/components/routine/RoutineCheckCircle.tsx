import { Circle, CircleCheckBig } from "lucide-react";

import { cn } from "@/lib/utils";

interface Props {
  checked: boolean;
  label: string;
  onToggle: () => void;
  disabled?: boolean;
}

/**
 * 습관 완료 체크 동그라미(○/●). 색 외에 기호(빈 원/체크 원)로 상태를 구분하고,
 * role=checkbox + aria-checked로 노출한다. native button이라 포커스·Enter 토글이 기본 동작한다.
 */
export function RoutineCheckCircle({
  checked,
  label,
  onToggle,
  disabled,
}: Props) {
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={onToggle}
      className={cn(
        "shrink-0 rounded-full transition-colors",
        "focus-visible:ring-ring focus-visible:ring-2 focus-visible:outline-none",
        checked
          ? "text-primary"
          : "text-muted-foreground hover:text-foreground",
      )}
    >
      {checked ? (
        <CircleCheckBig className="size-5" />
      ) : (
        <Circle className="size-5" />
      )}
    </button>
  );
}
