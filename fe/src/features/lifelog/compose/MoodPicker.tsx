import { cn } from "@/lib/utils";

import type { Mood } from "../api/types";
import { MOODS } from "../lib/moods";

interface MoodPickerProps {
  value: Mood | null;
  onChange: (mood: Mood | null) => void;
}

/** 기분 선택. 같은 값을 다시 누르면 해제된다(선택 사항). */
export function MoodPicker({ value, onChange }: MoodPickerProps) {
  return (
    <div className="flex gap-1.5" role="group" aria-label="기분">
      {MOODS.map((mood) => {
        const selected = value === mood.value;
        return (
          <button
            key={mood.value}
            type="button"
            aria-label={mood.label}
            aria-pressed={selected}
            onClick={() => onChange(selected ? null : mood.value)}
            className={cn(
              "flex size-9 items-center justify-center rounded-full border text-lg transition-colors",
              selected
                ? "border-primary bg-primary/10"
                : "border-border hover:bg-muted",
            )}
          >
            {mood.emoji}
          </button>
        );
      })}
    </div>
  );
}
