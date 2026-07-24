import { X } from "lucide-react";
import { useState } from "react";

import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

import { useTagSuggestions } from "../hooks/useTagSuggestions";

interface TagInputProps {
  tags: string[];
  onChange: (tags: string[]) => void;
}

/** 태그 칩 + 자동완성 입력. Enter/쉼표로 추가, 중복은 무시한다. */
export function TagInput({ tags, onChange }: TagInputProps) {
  const [draft, setDraft] = useState("");
  const { data: suggestions } = useTagSuggestions(draft);

  const add = (raw: string) => {
    const name = raw.trim();
    if (name && !tags.includes(name)) {
      onChange([...tags, name]);
    }
    setDraft("");
  };

  const remove = (name: string) => onChange(tags.filter((t) => t !== name));

  const filteredSuggestions = (suggestions ?? []).filter(
    (s) => !tags.includes(s),
  );

  return (
    <div>
      <div className="flex flex-wrap items-center gap-1.5">
        {tags.map((tag) => (
          <span
            key={tag}
            className="bg-muted text-foreground/80 inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs"
          >
            #{tag}
            <button
              type="button"
              aria-label={`${tag} 태그 제거`}
              onClick={() => remove(tag)}
              className="hover:text-foreground"
            >
              <X className="size-3" />
            </button>
          </span>
        ))}
      </div>
      <div className="relative mt-1.5">
        <Input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === ",") {
              e.preventDefault();
              add(draft);
            } else if (e.key === "Backspace" && !draft && tags.length > 0) {
              remove(tags[tags.length - 1]);
            }
          }}
          placeholder="태그 입력 후 Enter"
          aria-label="태그 입력"
        />
        {draft.trim() && filteredSuggestions.length > 0 && (
          <ul
            className={cn(
              "border-border bg-background absolute z-10 mt-1 w-full rounded-md border shadow-md",
            )}
          >
            {filteredSuggestions.slice(0, 6).map((s) => (
              <li key={s}>
                <button
                  type="button"
                  onClick={() => add(s)}
                  className="hover:bg-muted w-full px-3 py-1.5 text-left text-sm"
                >
                  #{s}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
