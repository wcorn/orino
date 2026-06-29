import { cn } from "@/lib/utils";

import { PLAN_COLORS } from "./planColors";

interface ColorSwatchSelectorProps {
  /** 선택된 색 키(null이면 미선택). */
  value: string | null;
  onChange: (key: string) => void;
  /** 그룹 라벨 연결용 id(FormField labelId). */
  labelledBy?: string;
}

/** 주간 계획표 색 선택 스와치 묶음. 생성/편집 폼에서 공용. */
export function ColorSwatchSelector({
  value,
  onChange,
  labelledBy,
}: ColorSwatchSelectorProps) {
  return (
    <div className="flex gap-2" role="group" aria-labelledby={labelledBy}>
      {PLAN_COLORS.map((c) => (
        <button
          key={c.key}
          type="button"
          aria-label={c.label}
          aria-pressed={value === c.key}
          onClick={() => onChange(c.key)}
          className={cn(
            "size-6 rounded-full ring-offset-2 transition",
            c.swatch,
            value === c.key
              ? "ring-ring ring-2"
              : "opacity-70 hover:opacity-100",
          )}
        />
      ))}
    </div>
  );
}
